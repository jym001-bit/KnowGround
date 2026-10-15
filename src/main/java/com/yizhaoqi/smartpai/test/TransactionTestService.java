package com.yizhaoqi.smartpai.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("test")
public class TransactionTestService {

    @Autowired
    private TestEntityRepository testEntityRepository;

    @Lazy
    @Autowired
    private TransactionTestService self;

    public void testProtectedTransaction() {
        // This call will not be transactional
        protectedTransactionalMethod("test-protected");
    }

    public void testProtectedTransactionWithSelfProxy() {
        // This call will be transactional
        self.protectedTransactionalMethod("test-protected-proxy");
    }

    @Transactional
    protected void protectedTransactionalMethod(String name) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        testEntityRepository.save(entity);
        throw new RuntimeException("Rollback test for protected method");
    }
}
