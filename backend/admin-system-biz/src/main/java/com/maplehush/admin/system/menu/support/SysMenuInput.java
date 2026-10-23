package com.maplehush.admin.system.menu.support;

import com.maplehush.admin.system.menu.dto.SysMenuSaveDTO;
import com.maplehush.admin.system.menu.entity.SysMenu;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 显式转换白名单字段，不把客户端 Map 作为数据库实体更新
 */
@Component
public class SysMenuInput {
    private static final Set<String> BOOLEANS =
            Set.of(
                    "affixTab",
                    "breadcrumbUseActivePath",
                    "domCached",
                    "fullPathKey",
                    "hideChildrenInMenu",
                    "hideInBreadcrumb",
                    "hideInMenu",
                    "hideInTab",
                    "keepAlive",
                    "noBasicLayout",
                    "openInNewWindow");
    private static final Set<String> STRINGS =
            Set.of(
                    "activeIcon",
                    "activePath",
                    "badge",
                    "badgeType",
                    "badgeVariants",
                    "iframeSrc",
                    "link");
    private static final Set<String> INTEGERS = Set.of("affixTabOrder", "maxNumOfOpenTab");
    private final Set<String> components;
    private final JsonMapper json;

    public SysMenuInput(JsonMapper json) throws IOException {
        this.json = json;
        try (var stream = new ClassPathResource("menu-components.txt").getInputStream();
                var reader =
                        new java.io.BufferedReader(
                                new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
            components =
                    reader.lines()
                            .filter(line -> line.startsWith("/"))
                            .collect(Collectors.toUnmodifiableSet());
        }
    }

    public SysMenu convert(SysMenuSaveDTO dto) {
        if (!SysMenuRules.TYPES.contains(dto.type())) {
            throw SysMenuRules.invalid("菜单类型无效");
        }
        SysMenu menu = new SysMenu();
        menu.setParentId(SysMenuRules.parentId(dto.pid()));
        menu.setName(dto.name());
        menu.setType(dto.type());
        menu.setStatus(dto.status() == null ? 1 : dto.status());
        Map<String, Object> meta = new HashMap<>(dto.meta() == null ? Map.of() : dto.meta());
        menu.setTitle(text(meta.remove("title"), 200, true));
        menu.setIcon(text(meta.remove("icon"), 200, false));
        Object order = meta.remove("order");
        if (order != null && !(order instanceof Integer)) {
            throw SysMenuRules.invalid("排序值须为整数");
        }
        menu.setSort(order == null ? 0 : (Integer) order);
        // 表单清空已知的可选文本时按删除字段处理，不残留旧元数据。
        meta.entrySet()
                .removeIf(
                        entry ->
                                STRINGS.contains(entry.getKey())
                                        && (entry.getValue() == null
                                                || entry.getValue() instanceof String text
                                                        && text.isBlank()));
        validateMetadata(meta);
        menu.setMetaJson(meta.isEmpty() ? null : json.writeValueAsString(meta));
        String code = blank(dto.authCode());
        if (code != null && !code.matches("system:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*")) {
            throw SysMenuRules.invalid("权限码须为小写 system:resource:action");
        }
        menu.setAuthCode(code);
        menu.setRedirect(blank(dto.redirect()));
        menu.setComponent(blank(dto.component()));
        if ("button".equals(dto.type())) {
            if (code == null
                    || blank(dto.path()) != null
                    || menu.getComponent() != null
                    || menu.getRedirect() != null
                    || !meta.isEmpty()) {
                throw SysMenuRules.invalid("按钮须有操作码，不能设置路由或扩展元数据");
            }
        } else {
            if ("link".equals(dto.type()) && blank(dto.path()) == null) {
                menu.setPath(null);
            } else {
                SysMenuRules.path(dto.path());
                menu.setPath(dto.path());
            }
            switch (dto.type()) {
                case "catalog" -> {
                    if (code != null
                            || (menu.getComponent() != null
                                    && !(menu.getParentId() == null
                                            && "BasicLayout".equals(menu.getComponent())))) {
                        throw SysMenuRules.invalid("目录不设置权限码，布局组件只允许根目录 BasicLayout");
                    }
                }
                case "menu" -> {
                    if (menu.getComponent() == null || !components.contains(menu.getComponent())) {
                        throw SysMenuRules.invalid("页面组件不在已登记清单中，不能带 .vue 后缀");
                    }
                }
                case "embedded" -> {
                    if (!meta.containsKey("iframeSrc")
                            || (menu.getComponent() != null
                                    && !"IFrameView".equals(menu.getComponent()))) {
                        throw SysMenuRules.invalid("内嵌页须配置 iframeSrc 与 IFrameView");
                    }
                    menu.setComponent("IFrameView");
                }
                case "link" -> {
                    if (!meta.containsKey("link")
                            || menu.getComponent() != null
                            || menu.getRedirect() != null) {
                        throw SysMenuRules.invalid("外链须配置 link，不能设置组件或重定向");
                    }
                }
                default -> throw SysMenuRules.invalid("菜单类型无效");
            }
            if (!"embedded".equals(dto.type()) && meta.containsKey("iframeSrc")) {
                throw SysMenuRules.invalid("iframeSrc 只用于内嵌页面");
            }
            if (!"link".equals(dto.type()) && meta.containsKey("link")) {
                throw SysMenuRules.invalid("link 只用于外链页面");
            }
        }
        return menu;
    }

    private void validateMetadata(Map<String, Object> meta) {
        for (var entry : meta.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (BOOLEANS.contains(key) && value instanceof Boolean) {
                continue;
            }
            if (INTEGERS.contains(key) && value instanceof Integer) {
                continue;
            }
            if (STRINGS.contains(key)
                    && value instanceof String text
                    && text.length() <= 1000
                    && !text.isBlank()) {
                if (Set.of("iframeSrc", "link").contains(key)) {
                    safeUrl(text);
                }
                if ("activePath".equals(key)) {
                    SysMenuRules.path(text);
                }
                if ("badgeType".equals(key) && !Set.of("dot", "normal").contains(text)) {
                    throw SysMenuRules.invalid("徽标类型无效");
                }
                if ("badgeVariants".equals(key)
                        && !Set.of("default", "destructive", "primary", "success", "warning")
                                .contains(text)) {
                    throw SysMenuRules.invalid("徽标颜色无效");
                }
                continue;
            }
            throw SysMenuRules.invalid("菜单扩展字段不在白名单中或类型错误");
        }
    }

    private void safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null
                    || !Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw SysMenuRules.invalid("链接须为不含用户凭据的 HTTP(S) URL");
            }
        } catch (IllegalArgumentException exception) {
            throw SysMenuRules.invalid("链接格式错误");
        }
    }

    private String text(Object value, int max, boolean required) {
        if (!required && (value == null || value instanceof String text && text.isBlank())) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank() || text.length() > max) {
            throw SysMenuRules.invalid("菜单标题或图标无效");
        }
        return text;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
