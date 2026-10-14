package com.maplehush.admin.system.dept;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.system.dept.entity.SysDept;
import com.maplehush.admin.system.dept.support.SysDeptTree;

import org.junit.jupiter.api.Test;

import java.util.List;

class DeptTreeTest {
    @Test
    void treeOrdersSiblingsAndUsesStringIds() {
        var tree =
                SysDeptTree.assemble(
                        List.of(
                                dept(4, 1L, 10),
                                dept(1, null, 10),
                                dept(3, 1L, 10),
                                dept(2, null, 0)));
        assertEquals("2", tree.getFirst().id());
        assertEquals("0", tree.getFirst().pid());
        assertEquals(
                List.of("3", "4"), tree.get(1).children().stream().map(node -> node.id()).toList());
        assertTrue(tree.getFirst().children().isEmpty());
        assertTrue(SysDeptTree.assemble(List.of()).isEmpty());
    }

    @Test
    void cyclicAndMissingParentsAreRejected() {
        assertThrows(
                BusinessException.class,
                () -> SysDeptTree.assemble(List.of(dept(1, 2L, 0), dept(2, 1L, 0))));
        assertThrows(BusinessException.class, () -> SysDeptTree.assemble(List.of(dept(1, 1L, 0))));
        assertThrows(BusinessException.class, () -> SysDeptTree.assemble(List.of(dept(1, 99L, 0))));
    }

    private SysDept dept(long id, Long parent, int sort) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parent);
        dept.setName("部门" + id);
        dept.setSort(sort);
        dept.setStatus(1);
        return dept;
    }
}
