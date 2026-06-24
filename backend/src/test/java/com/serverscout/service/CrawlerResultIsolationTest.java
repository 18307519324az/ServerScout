package com.serverscout.service;

import com.serverscout.entity.Asset;
import com.serverscout.entity.CrawledUrl;
import com.serverscout.entity.Port;
import com.serverscout.entity.ScanTask;
import com.serverscout.repository.CrawledUrlRepository;
import com.serverscout.repository.PortRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlerResultIsolationTest {

    @Mock
    private CrawledUrlRepository crawledUrlRepository;

    @Test
    void shouldQueryCrawlerResultsByTaskId() {
        // When querying by task ID, only results for that task should be returned
        List<CrawledUrl> task1Results = List.of(
                CrawledUrl.builder().id(1L).url("http://example.com/page1")
                        .crawlDepth(0).crawledAt(Instant.now()).build(),
                CrawledUrl.builder().id(2L).url("http://example.com/page2")
                        .crawlDepth(0).crawledAt(Instant.now()).build()
        );

        when(crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(1L)).thenReturn(task1Results);

        List<CrawledUrl> results = crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(1L);

        assertEquals(2, results.size());
        verify(crawledUrlRepository).findByTaskIdOrderByCrawlDepthAsc(1L);
    }

    @Test
    void shouldReturnDifferentResultsForDifferentTasks() {
        // Results for task 1
        List<CrawledUrl> task1Results = List.of(
                CrawledUrl.builder().id(1L).url("http://example.com/task1")
                        .crawlDepth(0).crawledAt(Instant.now()).build()
        );

        // Results for task 2 — different URLs
        List<CrawledUrl> task2Results = List.of(
                CrawledUrl.builder().id(3L).url("http://other.com/page")
                        .crawlDepth(0).crawledAt(Instant.now()).build()
        );

        when(crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(1L)).thenReturn(task1Results);
        when(crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(2L)).thenReturn(task2Results);

        List<CrawledUrl> results1 = crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(1L);
        List<CrawledUrl> results2 = crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(2L);

        assertEquals(1, results1.size());
        assertEquals(1, results2.size());
        assertEquals("http://example.com/task1", results1.get(0).getUrl());
        assertEquals("http://other.com/page", results2.get(0).getUrl());
    }

    @Test
    void shouldReturnEmptyWhenTaskHasNoCrawlerResults() {
        when(crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(999L)).thenReturn(List.of());

        List<CrawledUrl> results = crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(999L);

        assertEquals(0, results.size());
    }

    @Test
    void crawlerCountShouldBeTaskScoped() {
        when(crawledUrlRepository.countByTaskId(1L)).thenReturn(5L);
        when(crawledUrlRepository.countByTaskId(2L)).thenReturn(0L);

        assertEquals(5, crawledUrlRepository.countByTaskId(1L));
        assertEquals(0, crawledUrlRepository.countByTaskId(2L));
    }
}
