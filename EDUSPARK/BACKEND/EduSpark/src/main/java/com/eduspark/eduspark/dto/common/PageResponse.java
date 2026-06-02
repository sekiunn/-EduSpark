package com.eduspark.eduspark.dto.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页响应
 *
 * @param <T> 数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 总页数
     */
    private Integer pages;

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    /**
     * 从 IPage 转换
     */
    public static <T> PageResponse<T> of(IPage<T> page) {
        int current = (int) page.getCurrent();
        int pages = (int) page.getPages();
        return PageResponse.<T>builder()
                .total(page.getTotal())
                .page(current)
                .size((int) page.getSize())
                .pages(pages)
                .list(page.getRecords())
                .hasNext(current < pages)
                .hasPrevious(current > 1)
                .build();
    }

    /**
     * 手动构建
     */
    public static <T> PageResponse<T> of(long total, int page, int size, List<T> list) {
        int pages = (int) ((total + size - 1) / size);
        return PageResponse.<T>builder()
                .total(total)
                .page(page)
                .size(size)
                .pages(pages)
                .list(list)
                .hasNext(page < pages)
                .hasPrevious(page > 1)
                .build();
    }
}
