package com.person.mryx.vo.product;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;

@Data
public class AttrGroupQueryVo {

	@ApiModelProperty(value = "组名")
	private String name;

}

