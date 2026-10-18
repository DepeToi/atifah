package com.atifah.common.core.result;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页记录列表。 */
    private final List<T> records;

    /** 记录总数。 */
    private final long total;

    /** 当前页码（从 1 起）。 */
    private final long page;

    /** 每页大小。 */
    private final long size;

    /**
     * 私有构造，构建分页结果。
     *
     * @param records 当前页记录列表
     * @param total 记录总数
     * @param page 当前页码（从 1 起）
     * @param size 每页大小
     */
    private PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 构建分页结果。
     *
     * @param records 当前页记录列表
     * @param total 记录总数
     * @param page 当前页码（从 1 起）
     * @param size 每页大小
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
