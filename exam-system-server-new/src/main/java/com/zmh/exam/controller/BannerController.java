package com.zmh.exam.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmh.exam.common.Result;
import com.zmh.exam.entity.Banner;
import com.zmh.exam.service.BannerService;
import com.zmh.exam.service.FileUploadService;
import io.minio.errors.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * 轮播图控制器 - 处理轮播图管理相关的HTTP请求
 * 包括图片上传、轮播图的CRUD操作、状态切换等功能
 */
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/banners")  // 轮播图API路径前缀
@CrossOrigin(origins = "*")  // 允许跨域访问
@RequiredArgsConstructor
@Slf4j
@Tag(name = "轮播图管理", description = "轮播图相关操作，包括图片上传、轮播图增删改查、状态管理等功能")  // Swagger API分组
public class BannerController {
    private final BannerService bannerService;

    
    /**
     * 上传轮播图图片
     * @param file 图片文件
     * @return 图片访问URL
     */
    @PostMapping("/upload-image")  // 处理POST请求
    @Operation(summary = "上传轮播图图片", description = "将图片文件上传到MinIO服务器，返回可访问的图片URL")  // API描述
    public Result<String> uploadBannerImage(
            @Parameter(description = "要上传的图片文件，支持jpg、png、gif等格式，大小限制5MB") 
            @RequestParam("file") MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String imgUrl =bannerService.upload(file);
        return Result.success(imgUrl, "图片上传成功");
    }

    /**
     * 实现逻辑：
     *    单表查询
     *    条件 = 激活状态 true
     *    根据优先级排序
     * 获取启用的轮播图（前台首页使用）
     * @return 轮播图列表
     */
    @GetMapping("/active")  // 处理GET请求
    @Operation(summary = "获取启用的轮播图", description = "获取状态为启用的轮播图列表，供前台首页展示使用")  // API描述
    public Result<List<Banner>> getActiveBanners() {
        LambdaQueryWrapper<Banner> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Banner::getIsActive, true);
        queryWrapper.orderByAsc(Banner::getSortOrder);
        List<Banner> list = bannerService.list(queryWrapper);
        log.info("查询前台需要的激活状态轮播信息业务执行成功！结果为：{}",list);

        return Result.success(list,"查询前端所需轮播图成功！");
    }
    
    /**
     * 实现逻辑：
     *    单表查询
     *    根据优先级倒序
     * 注意：
     *    逻辑删除
     *    逻辑删除字段和更新时间字段不返回
     *    创建时间格式化问题
     * 获取所有轮播图（管理后台使用）
     * @return 轮播图列表
     */
    @GetMapping("/list")  // 处理GET请求
    @Operation(summary = "获取所有轮播图", description = "获取所有轮播图列表，包括启用和禁用的，供管理后台使用")  // API描述
    public Result<List<Banner>> getAllBanners() {
        //1.拼接条件，进行排序
        LambdaQueryWrapper<Banner> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.orderByAsc(Banner::getSortOrder); //根据sort正序排序
        List<Banner> list = bannerService.list(lambdaQueryWrapper);
        log.info("查询所有后台需要的轮播信息业务执行成功！结果为：{}",list);
        return Result.success(list,"查询所有轮播图信息成功！");
    }

    /**
     * 实现思路：
     *    单表普通查询
     *    根据id查询
     *    注意逻辑删除，不过已经实现
     * 接口前期设计，但是前端并未调用，而是直接记录上次列表数据实现的！！
     * 根据ID获取轮播图
     * @param id 轮播图ID
     * @return 轮播图详情
     */
    @GetMapping("/{id}")  // 处理GET请求
    @Operation(summary = "根据ID获取轮播图", description = "根据轮播图ID获取单个轮播图的详细信息")  // API描述  
    public Result<Banner> getBannerById(@Parameter(description = "轮播图ID") @PathVariable Long id) {
        Banner banner = bannerService.getById(id);
        if (banner != null) {
            log.info("查询后台需要的轮播信息业务执行成功！结果为：{}",banner);
            return Result.success(banner,"查询轮播图详情成功！");
        }
        return Result.error("轮播图不存在");
    }

    /**
     * 实现思路：
     *    确认时间 创建和修改时间赋值
     *    确认激活状态true
     *    确认优先级没有默认 0
     *    进行保存即可
     *    单表动作，直接调用mybatis-plus业务层
     * 添加轮播图git
     * @param banner 轮播图对象
     * @return 操作结果
     */
    @PostMapping("/add")  // 处理POST请求
    @Operation(summary = "添加轮播图", description = "创建新的轮播图，需要提供图片URL、标题、跳转链接等信息")  // API描述
    public Result<String> addBanner(@RequestBody Banner banner) {
        bannerService.addBanner(banner);
        return Result.success("添加轮播图成功！");
    }

    /**
     * 更新轮播图
     * @param banner 轮播图对象
     * @return 操作结果
     */
    @PutMapping("/update")  // 处理PUT请求
    @Operation(summary = "更新轮播图", description = "更新轮播图的信息，包括图片、标题、跳转链接、排序等")  // API描述
    public Result<String> updateBanner(@RequestBody Banner banner) {
         bannerService.updateBanner(banner);
        return Result.success("更新轮播图成功!");
    }

    /**
     * 实现逻辑：
     *    单表操作
     *    逻辑删除
     *    已经配置直接调用业务即可
     * 删除轮播图
     * @param id 轮播图ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")  // 处理DELETE请求
    @Operation(summary = "删除轮播图", description = "根据ID删除指定的轮播图")  // API描述
    public Result<String> deleteBanner(@Parameter(description = "轮播图ID") @PathVariable Long id) {
        boolean b = bannerService.removeById(id);
        if (b){
            log.info("删除轮播图数据成功！删除id为：{}",id);
            return Result.success("轮播图数据删除成功！");
        }
        return Result.error("轮播图数据删除失败！");
    }

    /**
     * 实现逻辑：
     *    单表操作
     *    根据id更新banner状态
     *    状态可能是启用，也可以是禁用
     * 启用/禁用轮播图
     * @param id 轮播图ID
     * @param isActive 是否启用
     * @return 操作结果
     */
    @PutMapping("/toggle/{id}")  // 处理PUT请求
    @Operation(summary = "切换轮播图状态", description = "启用或禁用指定的轮播图，禁用后不会在前台显示")  // API描述
    public Result<String> toggleBannerStatus(
            @Parameter(description = "轮播图ID") @PathVariable Long id, 
            @Parameter(description = "是否启用，true为启用，false为禁用") @RequestParam Boolean isActive) {

        boolean updated = bannerService.update()
                .lambda()
                .set(Banner::getIsActive, isActive)
                .eq(Banner::getId, id)
                .update();
        if (updated) {
            log.info("轮播图状态修改成功，修改后的状态为：{}",isActive);
            return Result.success("轮播图状态修改成功！");
        }
        return Result.error("轮播图状态修改失败！");
    }
} 