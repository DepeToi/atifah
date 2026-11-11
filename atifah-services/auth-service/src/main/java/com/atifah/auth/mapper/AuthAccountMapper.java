package com.atifah.auth.mapper;

import com.atifah.auth.entity.AuthAccount;
import org.apache.ibatis.annotations.Param;

/**
 * 认证账号 Mapper。
 * 不继承 BaseMapper，SQL 全部写在 resources/mapper/AuthAccountMapper.xml。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface AuthAccountMapper {

    /**
     * 按手机号查询未删除的账号。
     *
     * @param phone 手机号
     * @return 账号；不存在时返回 null
     */
    AuthAccount selectByPhone(@Param("phone") String phone);

    /**
     * 新增认证账号，id 由调用方生成。
     *
     * @param account 待插入账号
     * @return 影响行数
     */
    int insert(AuthAccount account);
}
