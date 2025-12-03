package com.zmh.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zmh.exam.entity.ExamRecord;
import com.zmh.exam.vo.ExamRankingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @description 针对表【exam_record(考试记录表)】的数据库操作Mapper
 * @createDate 2025-06-20 22:37:43
 * @Entity com.zmh.exam.entity.ExamRecord
 */
@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /**
     * 查询考试排行榜（可按试卷筛选并限制返回数量）
     * @param paperId 试卷ID
     * @param limit 返回条数限制
     * @return 排行榜数据
     */
    @Select("<script>" +
            "SELECT " +
            "er.id AS id, " +
            "er.student_name AS studentName, " +
            "er.score AS score, " +
            "er.exam_id AS examId, " +
            "er.start_time AS startTime, " +
            "er.end_time AS endTime, " +
            "TIMESTAMPDIFF(MINUTE, er.start_time, er.end_time) AS duration, " +
            "p.name AS paperName, " +
            "p.total_score AS paperTotalScore " +
            "FROM exam_records er " +
            "LEFT JOIN paper p ON er.exam_id = p.id " +
            "WHERE er.is_deleted = 0 " +
            "<if test='paperId != null'> AND er.exam_id = #{paperId} </if> " +
            "ORDER BY er.score DESC, er.id ASC " +
            "<if test='limit != null and limit > 0'> LIMIT #{limit} </if>" +
            "</script>")
    List<ExamRankingVO> selectRanking(@Param("paperId") Integer paperId, @Param("limit") Integer limit);

}
