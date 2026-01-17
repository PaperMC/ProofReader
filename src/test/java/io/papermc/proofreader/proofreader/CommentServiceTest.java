package io.papermc.proofreader.proofreader;

import io.papermc.proofreader.proofreader.ProofReaderConfig.Config;
import io.papermc.proofreader.proofreader.github.GithubService;
import io.papermc.proofreader.proofreader.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    GithubService github;
    @Mock
    Config config;

    CommentService comments;

    @BeforeEach
    void setUp() {
        comments = new CommentService(github, config);
    }

    @Test
    void testNewCheckedBoxes_simple() {
        String oldComment = "- [ ] Foo\n- [ ] Bar";
        String newComment = "- [x] Foo\n- [ ] Bar";

        var result = comments.newCheckedBoxes(oldComment, newComment);

        assertEquals(1, result.size());
        assertTrue(result.contains("foo"));
    }

    @Test
    void testNewCheckedBoxes_multipleAndTrim() {
        String oldComment = "";
        String newComment = "- [x] Rebase PR\n- [x]  Force Update  \n- [ ] Approve for ProofReader";

        var result = comments.newCheckedBoxes(oldComment, newComment);

        assertEquals(2, result.size());
        assertTrue(result.contains("rebase pr"));
        assertTrue(result.contains("force update"));
    }

    @Test
    void testNewCheckedBoxes_unchangedCheckedNotIncluded() {
        String oldComment = "- [x] A\n- [ ] B";
        String newComment = "- [x] A\n- [ ] B";

        var result = comments.newCheckedBoxes(oldComment, newComment);

        assertTrue(result.isEmpty());
    }
}
