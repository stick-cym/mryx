package com.person.mryx.vo.sys;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;

@Data
public class RegionWareQueryVo {

	@ApiModelProperty(value = "关键字")
	private String keyword;

}

