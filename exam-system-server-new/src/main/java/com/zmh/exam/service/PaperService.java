package com.zmh.exam.service;

import com.zmh.exam.entity.Paper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zmh.exam.vo.PaperVo;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {

    Paper customPaperDetailById(Integer id);

    Paper customCreatePaper(PaperVo paperVo);
}