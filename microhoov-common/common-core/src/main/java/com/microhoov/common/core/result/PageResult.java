package com.microhoov.common.core.result;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果。
 */
@Getter
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<T> records;
    private final long total;
    private final long page;
    private final long size;

    private PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
