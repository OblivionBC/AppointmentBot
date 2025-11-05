package com.autosignup.core;

import com.autosignup.model.protocol.TypeWeeksBasedProtocol;
import com.autosignup.service.BotDBManager;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestTypeWeeksProtocol {
    @Autowired
    BotDBManager botDBManager;
    TypeWeeksBasedProtocol typeWeeksBasedProtocol;
    @Before
    void before() {
        typeWeeksBasedProtocol = new TypeWeeksBasedProtocol(botDBManager, 1);
    }
}
