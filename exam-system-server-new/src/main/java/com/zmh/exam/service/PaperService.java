package com.zmh.exam.service;

import com.zmh.exam.entity.Paper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zmh.exam.vo.AiPaperVo;
import com.zmh.exam.vo.PaperVo;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {

    /**
     * 获取试卷详情(深度查询试卷所有信息)
     * @param id
     * @return Paper
     */
    Paper customPaperDetailById(Integer id);

    Paper customCreatePaper(PaperVo paperVo);

    Paper customAiCreatePaper(AiPaperVo aiPaperVo);

    Paper customUpdatePaper(Integer id, PaperVo paperVo);

    void customUpdatePaperStatus(Integer id, String status);

    void customRemoveId(Integer id);
}