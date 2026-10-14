package com.maplehush.admin.system.dept.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 部门树节点，沿用 Playground 的 name/status/remark/children，并补充排序与时间。 */
public record SysDeptVO(String id, String pid, String name, Integer sort, Integer status, String remark,
                        LocalDateTime createTime, LocalDateTime updateTime, List<SysDeptVO> children) {}
