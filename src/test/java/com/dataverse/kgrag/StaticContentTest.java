package com.dataverse.kgrag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
public class StaticContentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testSuggestSubjectsHtml() throws Exception {
        mockMvc.perform(get("/suggest-subjects.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Suggest Subjects")))
                .andExpect(content().string(containsString("testMetadataSelect")));
    }

    @Test
    public void testTestMetadataJson() throws Exception {
        mockMvc.perform(get("/testmetadata.json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cellular correlates of gray matter volume changes")));
    }
}
