package com.rrs.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DynamicTablePageDTO {
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private Long total;

    public DynamicTablePageDTO() {}

    public DynamicTablePageDTO(List<String> columns, List<Map<String, Object>> rows, Long total) {
        this.columns = columns;
        this.rows = rows;
        this.total = total;
    }
}
