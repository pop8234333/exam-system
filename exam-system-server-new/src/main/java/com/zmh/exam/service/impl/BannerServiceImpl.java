package com.zmh.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.zmh.exam.entity.Banner;
import com.zmh.exam.mapper.BannerMapper;
import com.zmh.exam.service.BannerService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.service.FileUploadService;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 轮播图服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    private final FileUploadService fileUploadService;

    /**
     *
     * 实现逻辑：
     *   核心校验 【1.文件非空校验 2.格式校验需要是image 3. 文件大小限制】
     *   文件上传
     * 上传图片页面
     * @param file
     * @return 返回图片
     */
    @Override
    public String upload(MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        //1. 非空校验
        if (file.isEmpty()) {
            //配合全局异常处理，快速返回失败结果！！
            throw new RuntimeException("请选择要上传的文件！");
        }
        //2. 图片格式校验
        //获取文件的mimetype类型
        String contentType = file.getContentType();
        if (ObjectUtils.isEmpty(contentType) || !contentType.startsWith("image")) {
            //配合全局异常处理，快速返回失败结果！！
            throw new RuntimeException("轮播图只能上传图片文件！");
        }
        //3. 文件大小限制
        if (file.getSize() > 5 * 1024 * 1024) {
            //配合全局异常处理，快速返回失败结果！！
            throw new RuntimeException("图片文件大小不能超过5MB");
        }
        //4. 调用文件上传业务
        String imgUrl = fileUploadService.upload(file, "banners");
        //5. 返回结果
        log.info("完成banner图片上传，图片回显地址：{}",imgUrl);
        return imgUrl;
    }
}