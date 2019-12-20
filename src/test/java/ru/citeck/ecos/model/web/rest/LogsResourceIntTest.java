package ru.citeck.ecos.model.web.rest;

/**
 * Test class for the LogsResource REST controller.
 *
 * @see LogsResource
 */
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = EcosModelApp.class)
public class LogsResourceIntTest {

//    private MockMvc restLogsMockMvc;
//
//    @Before
//    public void setup() {
//        LogsResource logsResource = new LogsResource();
//        this.restLogsMockMvc = MockMvcBuilders
//            .standaloneSetup(logsResource)
//            .build();
//    }
//
//    @Test
//    public void getAllLogs() throws Exception {
//        restLogsMockMvc.perform(get("/management/logs"))
//            .andExpect(status().isOk())
//            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE));
//    }
//
//    @Test
//    public void changeLogs() throws Exception {
//        LoggerVM logger = new LoggerVM();
//        logger.setLevel("INFO");
//        logger.setName("ROOT");
//
//        restLogsMockMvc.perform(put("/management/logs")
//            .contentType(TestUtil.APPLICATION_JSON_UTF8)
//            .content(TestUtil.convertObjectToJsonBytes(logger)))
//            .andExpect(status().isNoContent());
//    }
//
//    @Test
//    public void testLogstashAppender() {
//        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//        assertThat(context.getLogger("ROOT").getAppender("ASYNC_LOGSTASH")).isInstanceOf(AsyncAppender.class);
//    }
}
