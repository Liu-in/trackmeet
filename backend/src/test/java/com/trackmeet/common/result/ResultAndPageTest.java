package com.trackmeet.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackmeet.common.base.BaseQuery;
import com.trackmeet.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一返回体、分页、分页查询基类与业务异常的单元测试。
 *
 * @author TrackMeet
 */
class ResultAndPageTest {

    @Test
    @DisplayName("R.ok：code=0、traceId 长度 16、timestamp 有效")
    void shouldBuildSuccessResult() {
        R<String> result = R.ok("hello");

        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("hello");
        assertThat(result.getTraceId()).hasSize(16);
        assertThat(result.getTimestamp()).isPositive();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("R.fail：使用错误码枚举与自定义提示")
    void shouldBuildFailResult() {
        R<Void> byCode = R.fail(ResultCode.RESULT_LOCKED);
        assertThat(byCode.getCode()).isEqualTo(40001);
        assertThat(byCode.getMessage()).isEqualTo("成绩已审核锁定，禁止直接修改");
        assertThat(byCode.isSuccess()).isFalse();

        R<Void> custom = R.fail(ResultCode.CONFLICT, "报名已存在");
        assertThat(custom.getCode()).isEqualTo(409);
        assertThat(custom.getMessage()).isEqualTo("报名已存在");
    }

    @Test
    @DisplayName("PageResult.of：从 MyBatis-Plus 分页对象换算总页数")
    void shouldConvertPageResult() {
        Page<String> page = new Page<>(2L, 10L);
        page.setRecords(List.of("a", "b"));
        page.setTotal(35L);

        PageResult<String> result = PageResult.of(page);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(35L);
        assertThat(result.getPage()).isEqualTo(2L);
        assertThat(result.getSize()).isEqualTo(10L);
        assertThat(result.getPages()).isEqualTo(4L);
    }

    @Test
    @DisplayName("BaseQuery：页码与每页条数做边界收敛（1~200）")
    void shouldGuardPageBounds() {
        BaseQuery query = new BaseQuery();
        query.setPage(0L);
        query.setSize(1000L);
        assertThat(query.pageNo()).isEqualTo(1L);
        assertThat(query.pageSize()).isEqualTo(200L);
        assertThat(query.isAsc()).isTrue();

        query.setSize(0L);
        query.setSortOrder("DESC");
        assertThat(query.pageSize()).isEqualTo(1L);
        assertThat(query.isAsc()).isFalse();
    }

    @Test
    @DisplayName("BizException：携带业务错误码")
    void shouldCarryBusinessCode() {
        BizException exception = new BizException(ResultCode.REG_LIMIT_EXCEEDED, "每单位每项限报 3 人");

        assertThat(exception.getCode()).isEqualTo(20003);
        assertThat(exception.getMessage()).isEqualTo("每单位每项限报 3 人");
        assertThatThrownBy(() -> {
            throw exception;
        }).isInstanceOf(BizException.class);
    }
}
