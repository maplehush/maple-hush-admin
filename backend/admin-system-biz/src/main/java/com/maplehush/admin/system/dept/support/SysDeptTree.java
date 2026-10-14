package com.maplehush.admin.system.dept.support;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.dept.entity.SysDept;
import com.maplehush.admin.system.dept.vo.SysDeptVO;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 统一部门树校验与排序，避免循环或断链被静默丢弃
 */
public final class SysDeptTree {
    private SysDeptTree() {}

    public static Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid("部门 ID 无效");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw invalid("部门 ID 超出范围");
        }
    }

    public static Long parentId(String value) {
        return value == null || value.isBlank() || "0".equals(value) ? null : id(value);
    }

    /**
     * 校验完整部门层级并组装稳定排序的树
     *
     * @param nodes 数据库节点或待写入的完整候选树
     * @return 校验通过且按 sort/id 排序的树
     */
    public static List<SysDeptVO> assemble(List<SysDept> nodes) {
        Map<Long, SysDept> indexed = new HashMap<>();
        nodes.forEach(node -> indexed.put(node.getId(), node));
        for (SysDept node : nodes) {
            var visited = new HashSet<Long>();
            SysDept current = node;
            while (current != null) {
                if (!visited.add(current.getId()) || visited.size() > 32) {
                    throw invalid("部门存在循环或超过 32 层");
                }
                Long parent = current.getParentId();
                if (parent == null) {
                    break;
                }
                current = indexed.get(parent);
                if (current == null) {
                    throw invalid("父部门不存在");
                }
            }
        }
        Map<Long, List<SysDept>> children = new HashMap<>();
        nodes.stream()
                .sorted(Comparator.comparing(SysDept::getSort).thenComparing(SysDept::getId))
                .forEach(
                        node ->
                                children.computeIfAbsent(
                                                node.getParentId(),
                                                ignored -> new java.util.ArrayList<>())
                                        .add(node));
        return tree(null, children);
    }

    private static List<SysDeptVO> tree(Long parent, Map<Long, List<SysDept>> children) {
        return children.getOrDefault(parent, List.of()).stream()
                .map(
                        node ->
                                new SysDeptVO(
                                        node.getId().toString(),
                                        node.getParentId() == null
                                                ? "0"
                                                : node.getParentId().toString(),
                                        node.getName(),
                                        node.getSort(),
                                        node.getStatus(),
                                        node.getRemark(),
                                        node.getCreateTime(),
                                        node.getUpdateTime(),
                                        tree(node.getId(), children)))
                .toList();
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
