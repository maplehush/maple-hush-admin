package com.maplehush.admin.system.dict.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.system.dict.dto.SysDictItemSaveDTO;
import com.maplehush.admin.system.dict.dto.SysDictQueryDTO;
import com.maplehush.admin.system.dict.dto.SysDictTypeSaveDTO;
import com.maplehush.admin.system.dict.vo.SysDictItemVO;
import com.maplehush.admin.system.dict.vo.SysDictOptionVO;
import com.maplehush.admin.system.dict.vo.SysDictTypeVO;

import java.util.List;

/**
 * 字典类型与字典项业务；类型行锁保护默认项切换与关联删除
 */
public interface ISysDictService {
    /**
     * 分页查询字典类型，包含停用类型
     *
     * @param query 已验证的类型分页条件
     * @return 当前页及总数
     */
    PageResult<SysDictTypeVO> listTypes(SysDictQueryDTO query);

    /**
     * 查询类型详情，不使用编码替代数据库 ID
     *
     * @param id 类型 ID
     * @return 类型详情；不存在时抛出 NOT_FOUND
     */
    SysDictTypeVO getType(Long id);

    /**
     * 创建编码稳定且唯一的字典类型
     *
     * @param dto 新建类型表单
     * @return 新建类型 ID
     * @throws BusinessException 缺少编码、编码重复或操作者授权已变化
     */
    Long saveType(SysDictTypeSaveDTO dto);

    /**
     * 更新类型名称、备注及可选状态，不改动编码
     *
     * @param id 类型 ID
     * @param dto 全量文字资料，省略状态保留；不允许改码
     */
    void updateType(Long id, SysDictTypeSaveDTO dto);

    /**
     * 删除空字典类型，关联项必须先显式删除
     *
     * @param id 类型 ID；存在任何字典项时拒绝删除
     * @throws BusinessException 类型不存在、仍有字典项或权限不足
     */
    void removeType(Long id);

    /**
     * 分页查询指定类型的全部管理项
     *
     * @param query 已验证的字典项分页条件
     * @return 当前页及总数，包含停用项
     */
    PageResult<SysDictItemVO> listItems(SysDictQueryDTO query);

    /**
     * 查询字典项管理详情
     *
     * @param id 字典项 ID
     * @return 字典项详情
     */
    SysDictItemVO getItem(Long id);

    /**
     * 在类型行锁保护下创建字典项，保持同类型值唯一及至多一个默认项
     *
     * @param dto 字典项表单；设为默认时在事务内撤销旧默认
     * @return 新建字典项 ID
     * @throws BusinessException 类型不存在、值重复或无写入权限；失败回滚旧默认标记
     */
    Long saveItem(SysDictItemSaveDTO dto);

    /**
     * 更新字典项，在同一事务内切换默认，保留省略的状态/排序/默认标记
     *
     * @param id 字典项 ID
     * @param dto 全量文本/颜色/备注；状态、排序、默认标记省略保留；不允许跨类型移动
     */
    void updateItem(Long id, SysDictItemSaveDTO dto);

    /**
     * 删除字典项，不隐式指定下一个默认项
     *
     * @param id 字典项 ID；删除默认项后不自动提升其他项
     */
    void removeItem(Long id);

    /**
     * 按稳定编码提供业务页面所需的有效选项
     *
     * @param code 字典类型编码
     * @return 启用类型下的启用选项；停用类型返回空列表，未知编码抛出 NOT_FOUND
     */
    List<SysDictOptionVO> listOptions(String code);
}
