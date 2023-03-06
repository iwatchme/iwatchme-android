package com.iwatchme.customcoroutine.coroutine;

import org.jetbrains.annotations.NotNull;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlinx.coroutines.DelayKt;

public class KotlinCoroutineImpl implements Continuation<Object> {

    private int label =0;
    private Continuation<Unit> completion;

    public KotlinCoroutineImpl(Continuation<Unit> continuation) {
        this.completion = continuation;
    }

    @NotNull
    @Override
    public CoroutineContext getContext() {
        return EmptyCoroutineContext.INSTANCE;
    }

    @Override
    public void resumeWith(@NotNull Object o) {
        Object result = o;

        try {
            switch (label) {
                case 0:
                    KotlinCoroutineKt.Logger("1");
                    result = KotlinCoroutineKt.returnSuspend(this);
                    label++;
                    if (isSuspended(result)) {
                        return;
                    }
                case 1:
                    KotlinCoroutineKt.Logger((String) result);
                    KotlinCoroutineKt.Logger("2");
                    result =  DelayKt.delay(1000, this);
                    label++;
                    if (isSuspended(result)) {
                        return;
                    }
                case 2:
                    KotlinCoroutineKt.Logger("3");
                    result =  KotlinCoroutineKt.returnImmediately(this);
                    label++;
                    if (isSuspended(result)){
                        return;
                    }

                case 3:
                    KotlinCoroutineKt.Logger((String) result);
                    KotlinCoroutineKt.Logger("4");

            }

            completion.resumeWith(Unit.INSTANCE);
        } catch (Exception e) {
            completion.resumeWith(e);

        }


    }

    private boolean isSuspended(Object o) {
     return  o == IntrinsicsKt.getCOROUTINE_SUSPENDED();
    }
}
