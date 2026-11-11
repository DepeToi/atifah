package com.atifah.common.mybatis.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用实体基类。
 * 所有 SQL 走 Mapper XML（ADR 0002）：id 由 Service 用 IdWorker 生成，
 * createdAt / updatedAt 由数据库默认值维护，deleted 由 XML 显式处理。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，雪花 ID，由 Service 在插入前生成。 */
    private Long id;

    /** 创建时间，由数据库默认值维护。 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库 ON UPDATE 维护。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0=未删除，1=已删除；由 XML 显式处理。 */
    private Integer deleted;
}
