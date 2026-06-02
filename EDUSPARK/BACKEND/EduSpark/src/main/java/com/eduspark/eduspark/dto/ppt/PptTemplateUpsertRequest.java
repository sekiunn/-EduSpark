package com.eduspark.eduspark.dto.ppt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptTemplateUpsertRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    @NotNull(message = "场景不能为空")
    private Long sceneId;

    @NotNull(message = "风格不能为空")
    private Long styleId;

    private String coverUrl;

    private List<String> previewImages;

    private String description;

    /**
     * 渲染模板键。允许留空——后端会从 templateCode/name 自动派生。第二版下这是个内部字段，
     * 通常管理员不再手填。
     */
    private String engineTemplateKey;

    private String promptHint;

    private String blueprintConfigJson;

    /**
     * 渲染配置 JSON。第二版下来源有两种：
     *   1. 上传 pptx 后由 parseTemplateMarkers 自动生成的 TemplateStructure JSON
     *   2. 配色种子模板的配色字典
     * 通常前端只读展示，不让管理员手编辑。
     */
    private String renderConfigJson;

    private Integer enabled;

    private Integer isDefault;

    private Integer sort;

    private Integer version;

    private String templateFilePath;

    /**
     * 当用户在新建/编辑对话框中先调用 pre-parse 上传了 pptx 时，前端把返回的
     * pendingFileKey 带过来。后端在 create/update 时把 _pending/&lt;key&gt;.pptx
     * move 到正式位置并写入 templateFilePath + renderConfigJson。
     */
    private String pendingFileKey;
}
