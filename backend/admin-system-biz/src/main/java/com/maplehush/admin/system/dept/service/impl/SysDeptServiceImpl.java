package com.maplehush.admin.system.dept.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.dept.dto.SysDeptSaveDTO;
import com.maplehush.admin.system.dept.entity.SysDept;
import com.maplehush.admin.system.dept.mapper.SysDeptMapper;
import com.maplehush.admin.system.dept.service.ISysDeptService;
import com.maplehush.admin.system.dept.support.SysDeptTree;
import com.maplehush.admin.system.dept.vo.SysDeptVO;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IPermissionService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 部门层级管理，不把部门状态或归属视为登录/数据授权条件
 */
@Service
public class SysDeptServiceImpl implements ISysDeptService {
    private final SysDeptMapper mapper;
    private final IPermissionService permissions;
    private final PermissionMapper users;

    public SysDeptServiceImpl(
            SysDeptMapper mapper, IPermissionService permissions, PermissionMapper users) {
        this.mapper = mapper;
        this.permissions = permissions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SysDeptVO> listDepts() {
        return SysDeptTree.assemble(listNodes());
    }

    @Transactional
    @OperationLog(value = "system:dept:create", targetType = "dept")
    @Override
    public Long saveDept(SysDeptSaveDTO dto) {
        lockAndValidateOperator();
        SysDept candidate = convert(dto);
        candidate.setId(-1L);
        var nodes = new ArrayList<>(listNodes());
        nodes.add(candidate);
        SysDeptTree.assemble(nodes);
        candidate.setId(null);
        try {
            if (mapper.insert(candidate) != 1) {
                throw new IllegalStateException("Department insert did not affect one row");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return candidate.getId();
    }

    @Transactional
    @OperationLog(value = "system:dept:update", targetType = "dept", targetIdArgument = 0)
    @Override
    public void updateDept(Long id, SysDeptSaveDTO dto) {
        lockAndValidateOperator();
        var nodes = new ArrayList<>(listNodes());
        SysDept original =
                nodes.stream()
                        .filter(node -> id.equals(node.getId()))
                        .findFirst()
                        .orElseThrow(this::notFound);
        SysDept candidate = convert(dto);
        candidate.setId(id);
        nodes.remove(original);
        nodes.add(candidate);
        SysDeptTree.assemble(nodes);
        try {
            if (mapper.updateDept(candidate) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Transactional
    @OperationLog(value = "system:dept:delete", targetType = "dept", targetIdArgument = 0)
    @Override
    public void removeDept(Long id) {
        lockAndValidateOperator();
        if (mapper.lockDept(id) == null) {
            throw notFound();
        }
        if (mapper.selectCount(Wrappers.<SysDept>lambdaQuery().eq(SysDept::getParentId, id)) > 0
                || mapper.countUsers(id) > 0) {
            throw conflict();
        }
        try {
            // 并发用户分配可能发生在快照建立后；最终仍由外键拒绝受引用删除。
            if (mapper.deleteById(id) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private void lockAndValidateOperator() {
        if (mapper.lockTreeWrites() == null) {
            throw new IllegalStateException("Active builtin role is required");
        }
        var context = permissions.current();
        var user = users.getUser(context.user().getId());
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || Boolean.TRUE.equals(user.getMustChangePassword())
                || !Objects.equals(user.getAuthVersion(), context.user().getAuthVersion())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "授权已变化，请重新登录");
        }
    }

    private SysDept convert(SysDeptSaveDTO dto) {
        SysDept dept = new SysDept();
        dept.setParentId(SysDeptTree.parentId(dto.pid()));
        dept.setName(dto.name().strip());
        dept.setSort(dto.sort() == null ? 0 : dto.sort());
        dept.setStatus(dto.status() == null ? 1 : dto.status());
        dept.setRemark(dto.remark() == null || dto.remark().isBlank() ? null : dto.remark());
        return dept;
    }

    private List<SysDept> listNodes() {
        return mapper.selectList(
                Wrappers.<SysDept>lambdaQuery()
                        .orderByAsc(SysDept::getSort)
                        .orderByAsc(SysDept::getId));
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "部门仍有关联用户或子部门，或存在关联冲突");
    }
}
