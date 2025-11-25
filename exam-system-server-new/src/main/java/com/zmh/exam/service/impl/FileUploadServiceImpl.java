package com.zmh.exam.service.impl;

import com.zmh.exam.config.properties.MinioProperties;
import com.zmh.exam.service.FileUploadService;
import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * projectName: com.zmh.exam.service.impl
 *
 * @author: 赵伟风
 * description:
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String upload(MultipartFile file, String folder) throws IOException, NoSuchAlgorithmException, InvalidKeyException, ServerException, InsufficientDataException, ErrorResponseException, InvalidResponseException, XmlParserException, InternalException {
        String endpoint = minioProperties.getEndpoint();
        String bucketName = minioProperties.getBucketName();
        String accessKey = minioProperties.getAccessKey();
        String secretKey = minioProperties.getSecretKey();

            //1.登录minio端点
            //构造MinIO Client （登录）
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            //2.检查桶是否存在
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                //3.不存在，创建通，并设置访问权限
                //创建hello-minio桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                //设置hello-minio桶的访问权限
                String policy = """
                        {
                          "Statement" : [ {
                            "Action" : "s3:GetObject",
                            "Effect" : "Allow",
                            "Principal" : "*",
                            "Resource" : "arn:aws:s3:::%s/*"
                          } ],
                          "Version" : "2012-10-17"
                        }""".formatted(bucketName);
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
            } else {
                System.out.println("Bucket 'hello-minio' already exists.");
            }
            //3. 处理上传的对象名（影响，minio桶中的文件结构！）
            //现在： 桶名 / folder / ai.png  缺点： 所有文件都平铺（banner，video）不好区分！ 核心缺点，可能覆盖！
            //小知识点： x/x/x.png -> exam0625 /x/x/ x.png
            //解决覆盖问题： 确保对象和文件的名字唯一即可！！ uuid - - -
            //1.需要添加文件夹 2.添加uuid确保不重复
            String objectName = folder + "/" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/" +
                    UUID.randomUUID().toString().replaceAll("-","")+"_"+ file.getOriginalFilename();

            log.debug("文件上传核心业务方法，处理后的文件对象名：{}",objectName);

            //4.上传文件对象
            //使用putObject方法上传MultipartFile，而不是uploadObject方法
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            System.out.println("上传成功");
            //5.拼接访问地址
            //5. 拼接回显地址 【端点 + 桶 + 对象名】
            String url = String.join("/", minioProperties.getEndpoint(), minioProperties.getBucketName(), objectName);
            log.info("文件上传核心业务，完成{}文件上传，返回地址为：{}",objectName,url);
            return url;

    }
}
