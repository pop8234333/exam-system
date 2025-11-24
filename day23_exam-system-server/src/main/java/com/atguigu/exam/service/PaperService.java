package com.atguigu.exam.service;

import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {

    /**
     * 根据试卷id试卷详情
     *    试卷对象
     *    题目集合
     *    注意： 题目的选项sort正序
     *    注意： 所有题目根据类型排序
     * @param id 试卷id
     * @return
     */
    Paper customPaperDetailById(Long id);

    /**
     * 手动组卷
     * @param paperVo
     * @return
     */
    Paper customCreatePaper(PaperVo paperVo);

    /**
     * 智能组卷
     * @param aiPaperVo
     * @return
     */
    Paper customAiCreatePaper(AiPaperVo aiPaperVo);

    /**
     * 更新试卷内信息
     * @param id
     * @param paperVo
     * @return
     */
    Paper customUpdatePaper(Integer id, PaperVo paperVo);

    /**
     * 根据id修改状态
     * @param id
     * @param status
     */
    void customUpdatePaperStatus(Integer id, String status);

    /**
     * 根据id删除试卷功能
     * @param id
     */
    void customRemoveId(Integer id);
}