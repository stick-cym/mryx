package com.person.mryx.user.api;

import com.person.mryx.user.service.UserService;
import com.person.mryx.vo.user.LeaderAddressVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/leader")
public class LeaderAddressApiController {

    @Autowired
    private UserService userService;

    //根据userId查询提货点和团长信息
    @GetMapping("/inner/getUserAddressByUserId/{userId}")
    public LeaderAddressVo getUserAddressByUserId(@PathVariable("userId") Long userId) {
        return userService.getLeaderAddressByUserId(userId);
    }
}
