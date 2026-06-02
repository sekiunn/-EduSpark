package com.eduspark.eduspark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据手机号查询用户
     */
    @Select("SELECT * FROM sys_user WHERE phone = #{phone} AND is_deleted = 0 LIMIT 1")
    SysUser selectByPhone(@Param("phone") String phone);

    /**
     * 根据手机号查询用户（包含已删除）
     */
    @Select("SELECT * FROM sys_user WHERE phone = #{phone} LIMIT 1")
    SysUser selectByPhoneIncludeDeleted(@Param("phone") String phone);
}
