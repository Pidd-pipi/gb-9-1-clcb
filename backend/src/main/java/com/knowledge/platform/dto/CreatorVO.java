package com.knowledge.platform.dto;

import lombok.Data;

@Data
public class CreatorVO {
    private String id;
    private String username;
    private String avatar;

    public CreatorVO(String id, String username, String avatar) {
        this.id = id;
        this.username = username;
        this.avatar = avatar;
    }
}
