package com.maplehush.admin.system.log.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.system.log.dto.SysLogQueryDTO;
import com.maplehush.admin.system.log.vo.SysLoginLogVO;
import com.maplehush.admin.system.log.vo.SysOperationLogVO;

/**
 * 登录与操作日志只读查询，不生成新的操作审计
 */
public interface ISysLogQueryService {
    /**
     * 分页读取登录、退出及刷新失败快照
     * @param query 已校验条件
     * @return 字符串 ID 的分页日志
     */
    PageResult<SysLoginLogVO> loginLogs(SysLogQueryDTO query);

    /**
     * 分页读取关键管理操作，不暴露未经控制的摘要
     * @param query 已校验条件
     * @return 字符串 ID 的分页日志
     */
    PageResult<SysOperationLogVO> operationLogs(SysLogQueryDTO query);
}
