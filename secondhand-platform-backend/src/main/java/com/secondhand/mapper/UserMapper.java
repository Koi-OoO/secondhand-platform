package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问接口
 * 负责用户表的数据库访问操作，继承 MyBatis-Plus 的基础 CRUD 能力，
 * 同时补充按用户名查询等自定义 SQL 方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户信息
     *
     * 业务场景：用于登录校验、注册时用户名去重、根据用户名恢复用户会话等场景。
     *
     * @param username 用户名
     * @return User 用户实体，不存在时返回 null
     */
    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM user WHERE username = #{username} AND email = #{email}")
    User selectByUsernameAndEmail(@Param("username") String username, @Param("email") String email);

    @Select("SELECT * FROM user WHERE email = #{email} LIMIT 1")
    User selectByEmail(@Param("email") String email);

    /**
     * 清空指定用户的邮箱及验证状态。
     *
     * 邮箱解绑需要把 email 显式置为 NULL，
     * 但 MyBatis-Plus 的 updateById 默认会忽略 null 字段，无法清空邮箱列，
     * 因此这里使用显式 UPDATE 语句。
     *
     * @param userId 用户 ID
     * @return 受影响行数
     */
    @Update("UPDATE user SET email = NULL, email_verified = 0 WHERE id = #{userId}")
    int clearEmailById(@Param("userId") Long userId);
}
