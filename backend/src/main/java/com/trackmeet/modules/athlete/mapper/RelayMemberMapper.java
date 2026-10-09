package com.trackmeet.modules.athlete.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.athlete.entity.RelayMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 接力队员 Mapper。
 * <p>
 * 提供按报名记录物理删除的方法：relay_member 存在唯一键 {@code UK(registration_id, leg_no)}，
 * 逻辑删除会残留占位行导致同一棒次无法重写，故队员明细的「先删后插」采用物理删除。
 *
 * @author TrackMeet
 */
@Mapper
public interface RelayMemberMapper extends BaseMapper<RelayMember> {

    /**
     * 物理删除某条报名记录下的全部接力队员。
     *
     * @param meetId         届次 ID
     * @param registrationId 报名记录 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM relay_member WHERE meet_id = #{meetId} AND registration_id = #{registrationId}")
    int deletePhysicalByRegistrationId(@Param("meetId") Long meetId,
                                       @Param("registrationId") Long registrationId);
}
