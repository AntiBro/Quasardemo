package com.quasar.quasardemo.service;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.httpasyncclient.FiberCloseableHttpAsyncClient;
import co.paralleluniverse.strands.SuspendableRunnable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.IntChannel;
import com.quasar.quasardemo.feign.TestHttp;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @Author huaili
 * @Date 2019/3/1 17:08
 * @Description TODO
 **/
@Service
public class TestService {

    //static  final CloseableHttpAsyncClient client = FiberCloseableHttpAsyncClient.wrap(HttpAsyncClients.custom().setMaxConnPerRoute(20).setMaxConnTotal(20).build());

    @Autowired
    TestHttp testHttp;


    static CloseableHttpAsyncClient client = FiberCloseableHttpAsyncClient.wrap(HttpAsyncClients.
            custom().
            setMaxConnPerRoute(20*2000).
            setMaxConnTotal(20*2000).
            build());
   static {
       client.start();
   }

    ThreadPoolExecutor mypool = new ThreadPoolExecutor(8,200,200, TimeUnit.MILLISECONDS,new LinkedBlockingDeque<>(8000));

    public String getContent() throws ExecutionException, InterruptedException, IOException, SuspendExecution {

        //Channel<String> channels = Channels.newChannel(20);

        ArrayList<Channel<String>> list = new ArrayList<>();

        Map<Integer,String> ret = new ConcurrentHashMap<>();

        ArrayList<Integer> resFuture = new ArrayList();

        for(int i=0;i<20;i++){
            list.add(Channels.newChannel(1));
            resFuture.add(i);
        }




//        if(!client.isRunning()) {
//            client.start();
//        }

        StringBuffer s = new StringBuffer();

        StringBuffer buffer = new StringBuffer();

        for(int i=0;i<20;i++){
            final int k = i;
            Fiber<String> rel = new Fiber<String>(new SuspendableRunnable() {
                @Override
                public void run() throws SuspendExecution, InterruptedException {
                    list.get(k).send(testHttp.getBaiduContent());
                }
            }).start();
        }


        for(int i=0;i<20;i++){
            buffer.append(list.get(i).receive());
        }

//        ret = resFuture.parallelStream().collect(Collectors.toMap(k->k,k ->{
//            return testHttp.getBaiduContent();
//        }));
//
//
//        for (String e:ret.values()){
//            buffer.append(e);
//        }

//        int concurrencyLevel = 20;
//        StringBuffer bufferSec = new StringBuffer();
//
//
//        ArrayList<Future<HttpResponse>> futures = new ArrayList<>();
//        for (int i = 0; i < concurrencyLevel; i++) {
//            futures.add(client.execute(new HttpGet("http://www.baidu.com"), null));
//        }
//
//        for (Future<HttpResponse> future : futures) {
//            bufferSec.append(EntityUtils.toString(future.get().getEntity()));
//        }




        return buffer.toString();
    }
}
