package com.maplehush.admin.system.dict.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.dict.dto.SysDictItemSaveDTO;
import com.maplehush.admin.system.dict.dto.SysDictQueryDTO;
import com.maplehush.admin.system.dict.dto.SysDictTypeSaveDTO;
import com.maplehush.admin.system.dict.entity.SysDictItem;
import com.maplehush.admin.system.dict.entity.SysDictType;
import com.maplehush.admin.system.dict.mapper.SysDictItemMapper;
import com.maplehush.admin.system.dict.mapper.SysDictTypeMapper;
import com.maplehush.admin.system.dict.service.ISysDictService;
import com.maplehush.admin.system.dict.vo.SysDictItemVO;
import com.maplehush.admin.system.dict.vo.SysDictOptionVO;
import com.maplehush.admin.system.dict.vo.SysDictTypeVO;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.role.mapper.SysRoleMapper;
import com.maplehush.admin.system.user.mapper.SysUserMapper;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 字典事务边界；统一按授权互斥行、操作者、字典类型、字典项的顺序加锁
 */
@Service
public class SysDictServiceImpl implements ISysDictService {
    private final SysDictTypeMapper types;
    private final SysDictItemMapper items;
    private final SysRoleMapper roles;
    private final SysUserMapper users;
    private final IPermissionService permissions;

    public SysDictServiceImpl(
            SysDictTypeMapper types,
            SysDictItemMapper items,
            SysRoleMapper roles,
            SysUserMapper users,
            IPermissionService permissions) {
        this.types = types;
        this.items = items;
        this.roles = roles;
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<SysDictTypeVO> listTypes(SysDictQueryDTO query) {
        SFunction<SysDictType, ?> column =
                switch (query.sortBy()) {
                    case "id" -> SysDictType::getId;
                    case "dictCode" -> SysDictType::getDictCode;
                    case "name" -> SysDictType::getDictName;
                    case "createTime" -> SysDictType::getCreateTime;
                    default -> throw invalid("类型排序字段无效");
                };
        var wrapper =
                Wrappers.<SysDictType>lambdaQuery()
                        .like(query.dictCode() != null, SysDictType::getDictCode, query.dictCode())
                        .like(query.name() != null, SysDictType::getDictName, query.name())
                        .eq(query.status() != null, SysDictType::getStatus, query.status())
                        .orderBy(true, query.ascending(), column);
        if (!"id".equals(query.sortBy())) {
            wrapper.orderByAsc(SysDictType::getId);
        }
        var page = types.selectPage(new Page<SysDictType>(query.page(), query.pageSize()), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::typeVO).toList(), page.getTotal());
    }

    @Transactional(readOnly = true)
    @Override
    public SysDictTypeVO getType(Long id) {
        return typeVO(type(id));
    }

    @Transactional
    @OperationLog(value = "system:dict:create", targetType = "dict_type")
    @Override
    public Long saveType(SysDictTypeSaveDTO dto) {
        lockOperator("system:dict:create");
        if (dto.dictCode() == null) {
            throw invalid("创建字典类型须提供编码");
        }
        SysDictType value = new SysDictType();
        value.setDictCode(dto.dictCode());
        value.setDictName(dto.name().strip());
        value.setStatus(dto.status() == null ? 1 : dto.status());
        value.setRemark(blank(dto.remark()));
        try {
            if (types.insert(value) != 1) {
                throw new IllegalStateException("Dictionary type was not inserted");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return value.getId();
    }

    @Transactional
    @OperationLog(value = "system:dict:update", targetType = "dict_type", targetIdArgument = 0)
    @Override
    public void updateType(Long id, SysDictTypeSaveDTO dto) {
        lockOperator("system:dict:update");
        SysDictType previous = lockType(id);
        if (dto.dictCode() != null && !dto.dictCode().equals(previous.getDictCode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "字典编码创建后不可修改");
        }
        previous.setDictName(dto.name().strip());
        previous.setRemark(blank(dto.remark()));
        if (dto.status() != null) {
            previous.setStatus(dto.status());
        }
        if (types.updateType(previous) != 1) {
            throw notFound("字典类型");
        }
    }

    @Transactional
    @OperationLog(value = "system:dict:delete", targetType = "dict_type", targetIdArgument = 0)
    @Override
    public void removeType(Long id) {
        lockOperator("system:dict:delete");
        lockType(id);
        if (items.selectCount(
                        Wrappers.<SysDictItem>lambdaQuery().eq(SysDictItem::getDictTypeId, id))
                > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "字典类型仍有字典项，请先删除关联项");
        }
        try {
            if (types.deleteById(id) != 1) {
                throw notFound("字典类型");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<SysDictItemVO> listItems(SysDictQueryDTO query) {
        type(query.dictTypeId());
        SFunction<SysDictItem, ?> column =
                switch (query.sortBy()) {
                    case "sort" -> SysDictItem::getSort;
                    case "id" -> SysDictItem::getId;
                    case "label" -> SysDictItem::getLabel;
                    case "value" -> SysDictItem::getValue;
                    case "createTime" -> SysDictItem::getCreateTime;
                    default -> throw invalid("字典项排序字段无效");
                };
        var wrapper =
                Wrappers.<SysDictItem>lambdaQuery()
                        .eq(SysDictItem::getDictTypeId, query.dictTypeId())
                        .like(query.label() != null, SysDictItem::getLabel, query.label())
                        .eq(query.status() != null, SysDictItem::getStatus, query.status())
                        .orderBy(true, query.ascending(), column);
        if (!"id".equals(query.sortBy())) {
            wrapper.orderByAsc(SysDictItem::getId);
        }
        var page = items.selectPage(new Page<SysDictItem>(query.page(), query.pageSize()), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::itemVO).toList(), page.getTotal());
    }

    @Transactional(readOnly = true)
    @Override
    public SysDictItemVO getItem(Long id) {
        return itemVO(item(id));
    }

    @Transactional
    @OperationLog(value = "system:dict:create", targetType = "dict_item")
    @Override
    public Long saveItem(SysDictItemSaveDTO dto) {
        lockOperator("system:dict:create");
        Long typeId = positiveId(dto.dictTypeId());
        lockType(typeId);
        SysDictItem value = values(dto, null);
        value.setDictTypeId(typeId);
        // 类型行锁覆盖清旧默认、写入与提交；唯一值冲突时两步一并回滚。
        try {
            if (Boolean.TRUE.equals(value.getDefaultItem())) {
                items.clearDefaults(typeId);
            }
            if (items.insert(value) != 1) {
                throw new IllegalStateException("Dictionary item was not inserted");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return value.getId();
    }

    @Transactional
    @OperationLog(value = "system:dict:update", targetType = "dict_item", targetIdArgument = 0)
    @Override
    public void updateItem(Long id, SysDictItemSaveDTO dto) {
        lockOperator("system:dict:update");
        SysDictItem previous = lockItemInType(id);
        if (!positiveId(dto.dictTypeId()).equals(previous.getDictTypeId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "字典项不允许通过修改移动所属类型");
        }
        SysDictItem value = values(dto, previous);
        value.setId(id);
        value.setDictTypeId(previous.getDictTypeId());
        try {
            if (Boolean.TRUE.equals(value.getDefaultItem())) {
                items.clearDefaults(value.getDictTypeId());
            }
            if (items.updateItem(value) != 1) {
                throw notFound("字典项");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Transactional
    @OperationLog(value = "system:dict:delete", targetType = "dict_item", targetIdArgument = 0)
    @Override
    public void removeItem(Long id) {
        lockOperator("system:dict:delete");
        lockItemInType(id);
        if (items.deleteById(id) != 1) {
            throw notFound("字典项");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<SysDictOptionVO> listOptions(String code) {
        if (code == null || !code.matches("[a-z][a-z0-9_]{0,99}")) {
            throw invalid("字典编码无效");
        }
        SysDictType type =
                types.selectOne(
                        Wrappers.<SysDictType>lambdaQuery().eq(SysDictType::getDictCode, code));
        if (type == null) {
            throw notFound("字典类型");
        }
        if (type.getStatus() != 1) {
            return List.of();
        }
        return items
                .selectList(
                        Wrappers.<SysDictItem>lambdaQuery()
                                .eq(SysDictItem::getDictTypeId, type.getId())
                                .eq(SysDictItem::getStatus, 1)
                                .orderByAsc(SysDictItem::getSort, SysDictItem::getId))
                .stream()
                .map(
                        value ->
                                new SysDictOptionVO(
                                        value.getLabel(),
                                        value.getValue(),
                                        value.getColor(),
                                        value.getDefaultItem()))
                .toList();
    }

    private void lockOperator(String code) {
        if (roles.lockRoleWrites() == null) {
            throw new IllegalStateException("Active builtin role is required");
        }
        var context = permissions.current();
        var user = users.lockUser(context.user().getId());
        if (user == null
                || user.getStatus() != 1
                || Boolean.TRUE.equals(user.getMustChangePassword())
                || !Objects.equals(user.getAuthVersion(), context.user().getAuthVersion())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "授权已变化，请重新登录");
        }
        if (!context.permissions().codes().contains(code)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少字典操作权限");
        }
    }

    private SysDictType type(Long id) {
        SysDictType value = types.selectById(id);
        if (value == null) {
            throw notFound("字典类型");
        }
        return value;
    }

    private SysDictType lockType(Long id) {
        SysDictType value = types.lockType(id);
        if (value == null) {
            throw notFound("字典类型");
        }
        return value;
    }

    private SysDictItem item(Long id) {
        SysDictItem value = items.selectById(id);
        if (value == null) {
            throw notFound("字典项");
        }
        return value;
    }

    private SysDictItem lockItemInType(Long id) {
        SysDictItem initial = item(id);
        lockType(initial.getDictTypeId());
        SysDictItem value = items.lockItem(id);
        if (value == null) {
            throw notFound("字典项");
        }
        return value;
    }

    private SysDictItem values(SysDictItemSaveDTO dto, SysDictItem previous) {
        SysDictItem value = new SysDictItem();
        value.setLabel(dto.label().strip());
        value.setValue(dto.value().strip());
        value.setColor(
                dto.color() == null || dto.color().isEmpty()
                        ? null
                        : dto.color().toLowerCase(Locale.ROOT));
        value.setRemark(blank(dto.remark()));
        value.setStatus(
                dto.status() != null ? dto.status() : previous == null ? 1 : previous.getStatus());
        value.setSort(dto.sort() != null ? dto.sort() : previous == null ? 0 : previous.getSort());
        value.setDefaultItem(
                dto.defaultItem() != null
                        ? dto.defaultItem()
                        : previous == null ? false : previous.getDefaultItem());
        return value;
    }

    private Long positiveId(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }
            return Long.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("ID 必须为 Long 范围内的正整数");
        }
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private SysDictTypeVO typeVO(SysDictType type) {
        return new SysDictTypeVO(
                type.getId().toString(),
                type.getDictCode(),
                type.getDictName(),
                type.getStatus(),
                type.getRemark(),
                type.getCreateTime(),
                type.getUpdateTime());
    }

    private SysDictItemVO itemVO(SysDictItem item) {
        return new SysDictItemVO(
                item.getId().toString(),
                item.getDictTypeId().toString(),
                item.getLabel(),
                item.getValue(),
                item.getColor(),
                item.getSort(),
                item.getDefaultItem(),
                item.getStatus(),
                item.getRemark(),
                item.getCreateTime(),
                item.getUpdateTime());
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException notFound(String resource) {
        return new BusinessException(ErrorCode.NOT_FOUND, resource + "不存在");
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "类型编码、同类型字典值重复或有关联状态冲突");
    }
}
