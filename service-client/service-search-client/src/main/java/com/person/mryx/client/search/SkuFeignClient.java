package com.person.mryx.client.search;


import com.person.mryx.model.search.SkuEs;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient("service-search")
public interface SkuFeignClient {

    @GetMapping("/api/search/sku/inner/findHotSkuList")
    List<SkuEs> findHotSkuList();

    @GetMapping("/api/search/sku/inner/incrHotScore/{skuId}")
    void incrHotScore(@PathVariable("skuId") Long skuId);
}
