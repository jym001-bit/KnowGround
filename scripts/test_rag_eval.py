import unittest

from rag_eval import evaluate, parse_judge, retrieval_metrics, review_agreement, sample_reviews


class RagEvalTest(unittest.TestCase):
    def setUp(self):
        self.dataset = [
            {"id": "found", "question": "q1", "referenceAnswer": "a1", "expectedChunks": ["f#1", "f#2"]},
            {"id": "missed", "question": "q2", "referenceAnswer": "a2", "expectedChunks": ["g#1"]},
        ]
        self.retrieval = [
            {"id": "found", "results": [
                {"fileMd5": "f", "chunkId": 2}, {"fileMd5": "f", "chunkId": 2},
                {"fileMd5": "x", "chunkId": 1}, {"fileMd5": "f", "chunkId": 1}]},
            {"id": "missed", "results": [{"fileMd5": "x", "chunkId": 1}]},
        ]
        self.answers = [{"id": "found", "answer": "wrong"}, {"id": "missed", "answer": "wrong"}]

    def test_retrieval_metrics_count_all_relevant_chunks_and_deduplicate_results(self):
        report = evaluate(self.dataset, self.retrieval, self.answers, 2,
                          lambda *_: {"score": 1, "reason": "incorrect"})
        self.assertEqual(report["summary"]["recallAtK"], 0.25)
        self.assertEqual(report["summary"]["mrrAtK"], 0.5)
        self.assertEqual(report["cases"][0]["rankedChunks"], ["f#2", "x#1", "f#1"])
        self.assertEqual(report["summary"]["attributionCounts"],
                         {"pass": 0, "没搜到": 1, "搜到了没用对": 1})

    def test_human_review_agreement_and_calibration_warning(self):
        report = evaluate(self.dataset, self.retrieval, self.answers, 2,
                          lambda *_: {"score": 4, "reason": "looks correct"})
        sample = sample_reviews(report, 20, 42)
        self.assertEqual(len(sample), 2)
        for row in sample:
            row["humanScore"] = 0
        result = review_agreement(report, sample)
        self.assertEqual(result["agreement"], 0)
        self.assertTrue(result["calibrationNeeded"])
        self.assertEqual(result["message"], "评委需要重新校准")

    def test_invalid_judge_response_and_missing_ids_are_not_silently_scored(self):
        with self.assertRaises(ValueError):
            parse_judge('{"score": 9, "reason": "bad"}')
        with self.assertRaises(ValueError):
            evaluate(self.dataset, self.retrieval[:1], self.answers, 2,
                     lambda *_: {"score": 5, "reason": "fine"})
        with self.assertRaises(ValueError):
            evaluate([], [], [], 2, lambda *_: {"score": 5, "reason": "fine"})
        stale = [{**self.retrieval[0], "question": "different"}, self.retrieval[1]]
        with self.assertRaises(ValueError):
            evaluate(self.dataset, stale, self.answers, 2,
                     lambda *_: {"score": 5, "reason": "fine"})


if __name__ == "__main__":
    unittest.main()
