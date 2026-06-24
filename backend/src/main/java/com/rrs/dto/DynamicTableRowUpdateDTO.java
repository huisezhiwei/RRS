package com.rrs.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DynamicTableRowUpdateDTO {
    private Map<String, Object> data;
}
