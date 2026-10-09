package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量生成标准项目结果视图对象。
 *
 * @author TrackMeet
 */
@Data
public class EventBatchGenerateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功创建数量 */
    private Integer createdCount = 0;

    /** 跳过数量（同名 + 同组别 + 同性别已存在） */
    private Integer skippedCount = 0;

    /** 成功创建的项目名称列表 */
    private List<String> createdNames = new ArrayList<>();

    /** 跳过的项目名称列表 */
    private List<String> skippedNames = new ArrayList<>();

    /**
     * 累加一个成功创建的项目名称。
     *
     * @param name 项目名称
     */
    public void addCreated(String name) {
        this.createdNames.add(name);
        this.createdCount = this.createdNames.size();
    }

    /**
     * 累加一个被跳过的项目名称。
     *
     * @param name 项目名称
     */
    public void addSkipped(String name) {
        this.skippedNames.add(name);
        this.skippedCount = this.skippedNames.size();
    }
}
