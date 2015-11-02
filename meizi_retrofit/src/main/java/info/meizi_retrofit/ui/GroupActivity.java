package info.meizi_retrofit.ui;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewTreeObserver;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import info.meizi_retrofit.R;
import info.meizi_retrofit.adapter.GroupAdapter;
import info.meizi_retrofit.base.BaseActivity;
import info.meizi_retrofit.model.Content;
import info.meizi_retrofit.net.ContentApi;
import info.meizi_retrofit.net.ContentParser;
import info.meizi_retrofit.utils.StringConverter;
import info.meizi_retrofit.utils.SystemBarTintManager;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by Mr_Wrong on 15/10/31.
 */
public class GroupActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener {
    @Bind(R.id.group_recyclerview)
    RecyclerView mRecyclerview;
    @Bind(R.id.group_refresher)
    SwipeRefreshLayout mRefresher;
    @Bind(R.id.group_toolbar)
    Toolbar mToolbar;
    private String groupid;
    public int index = -1;
    public int color;
    private GroupAdapter mAdapter;
    private ContentApi mApi;
    private List<Content> lists = new ArrayList<>();
    private StaggeredGridLayoutManager layoutManager;
    private Integer count = 0;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_layout);
        ButterKnife.bind(this);

        mRefresher.setOnRefreshListener(this);
        setSupportActionBar(mToolbar);
        mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                supportFinishAfterTransition();
            }
        });

        mApi = createApi();
        groupid = getIntent().getStringExtra("groupid");
        color = getIntent().getIntExtra("color", getResources().getColor(R.color.app_primary_color));

        setSystemBar();

        mRefresher.setColorSchemeColors(color);
        sendToLoad();

        mAdapter = new GroupAdapter(this) {
            @Override
            protected void onItemClick(View v, int position) {
                startLargePicActivity(v, position);
            }
        };
        layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerview.setLayoutManager(layoutManager);
        mRecyclerview.setAdapter(mAdapter);

    }


    private void setSystemBar() {
        SystemBarTintManager tintManager = new SystemBarTintManager(this);
        tintManager.setStatusBarTintEnabled(true);
        mToolbar.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            tintManager.setStatusBarTintColor(color);
        }
    }


    private void sendToLoad() {
        mRefresher.setRefreshing(true);
        //先去获取count  然后根据count去查询全部的content
        mSubscriptions.add(mApi.getContentCount(groupid)
                .flatMap(new Func1<String, Observable<Integer>>() {
                    @Override
                    public Observable<Integer> call(String s) {
                        return Observable.just(ContentParser.getCount(s));
                    }
                })
                .flatMap(new Func1<Integer, Observable<List<Content>>>() {
                    @Override
                    public Observable<List<Content>> call(Integer integer) {
                        count = integer;
                        return mListObservable;
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mListSubscriber));
    }

    Observable<List<Content>> mListObservable = Observable.create(new Observable.OnSubscribe<List<Content>>() {
        @Override
        public void call(final Subscriber<? super List<Content>> subscriber) {
            for (int i = 1; i < count + 1; i++) {
                mApi.getContent(groupid, i)
                        .map(new Func1<String, Content>() {
                            @Override
                            public Content call(String s) {
                                Content content = null;//这里没有使用接口，是因为一定要setEndpoint 所以作罢。。
                                try {
                                    content = handleContent(ContentParser.ParserContent(s));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return content;
                            }
                        })
                        .subscribe(new Action1<Content>() {
                            @Override
                            public void call(Content content) {
                                content.setGroupid(groupid);
                                lists.add(content);
                                if (lists.size() >= count - 1) {//这里有时候会少一个。。为啥
                                    subscriber.onNext(lists);
                                }
                            }
                        });
            }

        }
    });


    Action1<List<Content>> mListSubscriber = new Action1<List<Content>>() {
        @Override
        public void call(List<Content> list) {
            mAdapter.replaceWith(list);
            mRefresher.setRefreshing(false);
        }
    };

    private Content handleContent(Content content) throws IOException {
        Response response = client.newCall(new Request.Builder().url(content.getUrl()).build()).execute();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(response.body().byteStream(), null, options);
        content.setImagewidth(options.outWidth);
        content.setImageheight(options.outHeight);
        return content;
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        supportStartPostponedEnterTransition();
        index = data.getIntExtra("index", 0);
        mRecyclerview.scrollToPosition(index);
        mRecyclerview.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerview.getViewTreeObserver().removeOnPreDrawListener(this);
                mRecyclerview.requestLayout();
                return true;
            }
        });
    }

    private ContentApi createApi() {
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint("http://www.mzitu.com/")
                .setConverter(new StringConverter())
                .setClient(new OkClient())
                .build();
        return adapter.create(ContentApi.class);
    }

    private void startLargePicActivity(View view, int position) {
        Intent intent = new Intent(this, LargePicActivity.class);
        intent.putExtra("index", position);
        intent.putExtra("groupid", groupid);
        ActivityOptionsCompat options = ActivityOptionsCompat
                .makeSceneTransitionAnimation(this, view, mAdapter.get(position).getUrl());
        startActivity(intent, options.toBundle());
    }

    @Override
    public void onRefresh() {
        sendToLoad();
    }
}
