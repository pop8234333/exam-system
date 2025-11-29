package com.zmh.exam.controller;


import com.zmh.exam.common.Result;
import com.zmh.exam.service.QuestionService;
import com.zmh.exam.vo.AiGenerateRequestVo;
import com.zmh.exam.vo.QuestionImportVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 题目批量管理控制器 - 仅做入参转发，业务逻辑在服务层
 */
@Slf4j
@RestController
@RequestMapping("/api/questions/batch")
@CrossOrigin(origins = "*")
@Tag(name = "题目批量操作", description = "题目批量管理相关操作，包括Excel导入、AI生成题目、批量验证等功能")
@RequiredArgsConstructor
public class QuestionBatchController {
    
    private final QuestionService questionService;

    /**
     * 下载Excel导入模板。
     * 说明：用于指导用户按约定格式填写题目数据；Controller 仅组装响应头，内容由服务层生成。
     */
    @GetMapping("/template")
    @Operation(summary = "下载Excel导入模板", description = "下载题目批量导入的Excel模板文件")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] data = questionService.generateImportTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "question_template.xlsx");
        return ResponseEntity.ok().headers(headers).body(data);
    }
    
    /**
     * 预览Excel文件内容（不入库）。
     * 说明：上传Excel后解析为题目列表并做基础校验，便于前端先行展示问题行，业务处理在服务层完成。
     */
    @PostMapping("/preview-excel")
    @Operation(summary = "预览Excel文件内容", description = "解析并预览Excel文件中的题目内容，不会导入到数据库")
    public Result<List<QuestionImportVo>> previewExcel(
            @Parameter(description = "Excel文件，支持.xls和.xlsx格式") @RequestParam("file") MultipartFile file) {
        return questionService.previewImport(file);
    }
    
    /**
     * 从Excel文件批量导入题目。
     * 说明：解析后直接入库，内部复用通用导入逻辑，失败会返回具体错误信息。
     */
    @PostMapping("/import-excel")
    @Operation(summary = "从Excel文件批量导入题目", description = "解析Excel文件并将题目批量导入到数据库")
    public Result<String> importFromExcel(
            @Parameter(description = "Excel文件，包含题目数据") @RequestParam("file") MultipartFile file) {
        return questionService.importFromExcel(file);
    }
    
    /**
     * 使用AI生成题目（预览，不入库）。
     * 说明：预留大模型接入点，当前服务层返回提示；Controller 保持统一入口。
     */
    @PostMapping("/ai-generate")
    @Operation(summary = "AI智能生成题目", description = "使用AI技术根据指定主题和要求智能生成题目，支持预览后再决定是否导入")
    public Result<List<QuestionImportVo>> generateQuestionsByAi(
            @RequestBody @Validated AiGenerateRequestVo request) {
        return questionService.generateQuestionsByAi(request);
    }
    
    /**
     * 批量导入题目（通用接口，支持Excel导入或AI生成后的确认导入）。
     * 说明：前端直接提交解析好的题目列表，服务层统一校验并落库。
     */
    @PostMapping("/import-questions")
    @Operation(summary = "批量导入题目", description = "将题目列表批量导入到数据库，支持Excel解析后的导入或AI生成后的确认导入")
    public Result<String> importQuestions(@RequestBody List<QuestionImportVo> questions) {
       return questionService.importQuestions(questions);
    }
    
    /**
     * 验证题目数据。
     * 说明：仅做校验不入库，返回首个错误信息或“验证通过”。
     */
    @PostMapping("/validate")
    @Operation(summary = "验证题目数据", description = "验证题目数据的完整性和格式正确性，返回验证结果和错误信息")
    public Result<String> validateQuestions(@RequestBody List<QuestionImportVo> questions) {
        return questionService.validateQuestions(questions);
    }
} 
