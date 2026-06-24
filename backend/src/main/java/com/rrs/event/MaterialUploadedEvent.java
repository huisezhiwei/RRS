package com.rrs.event;

import org.springframework.context.ApplicationEvent;

public class MaterialUploadedEvent extends ApplicationEvent {

    private final Long libraryId;
    private final Long materialId;

    public MaterialUploadedEvent(Object source, Long libraryId, Long materialId) {
        super(source);
        this.libraryId = libraryId;
        this.materialId = materialId;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public Long getMaterialId() {
        return materialId;
    }
}
