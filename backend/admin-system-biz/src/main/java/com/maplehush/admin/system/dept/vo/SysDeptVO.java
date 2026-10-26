package com.maplehush.admin.system.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门树节点，沿用 Playground 的 name/status/remark/children，并补充排序与时间
 *
 * @param id 业务资源 ID，以字符串返回，避免前端整数精度丢失
 * @param pid 父节点 ID；根节点使用空值或协议约定的根值
 * @param name 业务显示名称
 * @param sort 排序值；越小越靠前，同值按 ID 升序
 * @param status 启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理
 * @param remark 业务备注
 * @param createTime 创建时间，格式为东八区 yyyy-MM-dd HH:mm:ss
 * @param updateTime 最后更新时间，格式为东八区 yyyy-MM-dd HH:mm:ss
 * @param children 按稳定顺序排列的子节点；叶节点为空列表
 */
public record SysDeptVO(
        @Schema(description = "业务资源 ID，以字符串返回，避免前端整数精度丢失", example = "1") String id,
        @Schema(description = "父节点 ID；根节点使用空值或协议约定的根值") String pid,
        @Schema(description = "业务显示名称") String name,
        @Schema(description = "排序值；越小越靠前，同值按 ID 升序") Integer sort,
        @Schema(description = "启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Schema(description = "业务备注") String remark,
        @Schema(description = "创建时间，格式为东八区 yyyy-MM-dd HH:mm:ss") LocalDateTime createTime,
        @Schema(description = "最后更新时间，格式为东八区 yyyy-MM-dd HH:mm:ss") LocalDateTime updateTime,
        @Schema(description = "按稳定顺序排列的子节点；叶节点为空列表") List<SysDeptVO> children) {}
