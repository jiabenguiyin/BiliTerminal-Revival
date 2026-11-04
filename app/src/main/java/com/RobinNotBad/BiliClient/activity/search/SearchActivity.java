package com.RobinNotBad.BiliClient.activity.search;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.SearchHistoryAdapter;
import com.RobinNotBad.BiliClient.adapter.SearchSuggestionsAdapter;
import com.RobinNotBad.BiliClient.api.SearchApi;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.JsonUtil;
import com.RobinNotBad.BiliClient.util.LinkUrlUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Objects;

public class SearchActivity extends InstanceActivity {
    private String lastKeyword = "";
    private boolean hasSearched;
    private String[] searchCategories;
    private TabLayoutMediator categoryMediator;
    private TabLayout categoriesBar;
    private TextView categorySelector;
    private com.RobinNotBad.BiliClient.player.PlayerOptionsMenu categoryPicker;
    private float searchClockTextSize;
    private int resultsScrollState, categoryScrollDistance;
    private boolean categoryChangedThisDrag = true;
    private RecyclerView historyRecyclerview;
    private RecyclerView suggestionsRecyclerview;
    SearchHistoryAdapter searchHistoryAdapter;
    SearchSuggestionsAdapter searchSuggestionsAdapter;
    ViewPager2 viewPager;
    EditText keywordInput;
    private ConstraintLayout searchBar;
    private boolean refreshing = false;
    Handler handler;
    ArrayList<String> searchHistory;
    ArrayList<String> searchSuggestions;
    private Runnable suggestionRunnable;
    private boolean suggestionsEnabled;
    private String defaultSearchContent;
    private boolean defaultSearchContentEnabled;

    boolean tutorial_show;
    String classname;

    String[] specialList = {"心理疾病", "自杀", "自尽", "自残", "抑郁", "双相", "安眠药"};

    @SuppressLint({"MissingInflatedId", "NotifyDataSetChanged", "InflateParams"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            lastKeyword = savedInstanceState.getString("search_keyword", "");
            hasSearched = savedInstanceState.getBoolean("search_submitted", false);
        }
        searchCategories = getResources().getStringArray(R.array.search_categories);

        classname = getClass().getSimpleName();
        tutorial_show = SharedPreferencesUtil.getBoolean("tutorial_pager_" + classname, true);

        asyncInflate(R.layout.activity_search, (layoutView, resId) -> {
            Log.e("debug", "进入搜索页");

            TutorialHelper.showTutorialList(this, R.array.tutorial_search, 4);

            handler = new Handler();

            suggestionsEnabled = SharedPreferencesUtil.getBoolean("search_suggestions_enable", true);
            defaultSearchContentEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE, false);
            
            if (defaultSearchContentEnabled) {
                new Thread(() -> {
                    try {
                        defaultSearchContent = SearchApi.getDefaultSearchContent();
                        if (defaultSearchContent != null && !defaultSearchContent.isEmpty()) {
                            runOnUiThread(() -> keywordInput.setHint(defaultSearchContent));
                        }
                    } catch (Exception e) {
                        Log.e("SearchActivity", "获取默认搜索内容失败", e);
                    }
                }).start();
            }

            viewPager = findViewById(R.id.viewPager);

            View searchBtn = findViewById(R.id.search);
            keywordInput = findViewById(R.id.keywordInput);
            categorySelector = findViewById(R.id.search_category_picker);
            categorySelector.setOnClickListener(ignored -> showCategoryPicker());
            findViewById(android.R.id.content).addOnLayoutChangeListener((v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft) v.post(this::setRound);
            });
            searchBar = findViewById(R.id.searchbar);
            historyRecyclerview = findViewById(R.id.history_recyclerview);
            suggestionsRecyclerview = findViewById(R.id.suggestions_recyclerview);
            viewPager.setOffscreenPageLimit(1);

            keywordInput.setOnFocusChangeListener((view, b) -> {
                setSearchCategoriesVisible(true);
                if (b) {
                    // 获得焦点时，根据输入内容决定显示历史还是建议
                    String keyword = keywordInput.getText().toString();
                    if (keyword.isEmpty() || !suggestionsEnabled) {
                        historyRecyclerview.setVisibility(View.VISIBLE);
                        suggestionsRecyclerview.setVisibility(View.GONE);
                    } else {
                        historyRecyclerview.setVisibility(View.GONE);
                        suggestionsRecyclerview.setVisibility(View.VISIBLE);
                    }
                } else {
                    // 失去焦点时隐藏所有列表
                    historyRecyclerview.setVisibility(View.GONE);
                    suggestionsRecyclerview.setVisibility(View.GONE);
                }
            });
            historyRecyclerview.setVisibility(View.VISIBLE);
            suggestionsRecyclerview.setVisibility(View.GONE);
            FragmentStateAdapter vpfAdapter = new FragmentStateAdapter(this) {
                @Override
                public int getItemCount() {
                    return searchCategories.length;
                }

                @NonNull
                @Override
                public Fragment createFragment(int position) {
                    SearchFragment fragment;
                    switch (position) {
                        case 0: fragment = SearchVideoFragment.newInstance(); break;
                        case 1: fragment = SearchMediaFragment.newInstance(SearchApi.TYPE_BANGUMI); break;
                        case 2: fragment = SearchMediaFragment.newInstance(SearchApi.TYPE_FILM); break;
                        case 3: fragment = SearchArticleFragment.newInstance(); break;
                        case 4: fragment = SearchUserFragment.newInstance(); break;
                        case 5: fragment = SearchLiveFragment.newInstance(); break;
                        default: throw new IllegalArgumentException("Unknown search category: " + position);
                    }
                    if (hasSearched) fragment.update(lastKeyword);
                    return fragment;
                }
            };
            viewPager.setAdapter(vpfAdapter);
            TabLayout categories = findViewById(R.id.search_categories);
            categoriesBar = categories;
            categoryMediator = new TabLayoutMediator(categories, viewPager,
                    (tab, position) -> tab.setText(searchCategories[position]));
            categoryMediator.attach();
            setSearchCategoriesVisible(true);
            updateSearchCategory(viewPager.getCurrentItem());
            setRound();

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateSearchCategory(position);
                    setSearchCategoriesVisible(true);
                    categoryChangedThisDrag = true;
                    if (position != 0) {
                        if (tutorial_show) {
                            tutorial_show = false;
                            findViewById(R.id.text_tutorial_pager).setVisibility(View.GONE);
                            SharedPreferencesUtil.putBoolean("tutorial_pager_" + classname, false);
                        }
                    }

                    Fragment fragmentCurr = getSupportFragmentManager()
                            .findFragmentByTag("f" + viewPager.getCurrentItem());
                    if (fragmentCurr instanceof SearchFragment) onSearchFragmentReady((SearchFragment) fragmentCurr);

                    // 确保标签栏滚动到当前选中的标签
                    TabLayout categories = findViewById(R.id.search_categories);
                    if (categories != null && categories.getTabCount() > position) {
                        TabLayout.Tab tab = categories.getTabAt(position);
                        if (tab != null) {
                            tab.select();
                        }
                    }
                }
            });

            searchBtn.setOnClickListener(view -> searchKeyword(keywordInput.getText().toString()));
            searchBtn.setOnLongClickListener(this::jumpToTargetId);
            keywordInput.setOnEditorActionListener((textView, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || event != null
                        && KeyEvent.KEYCODE_ENTER == event.getKeyCode() && KeyEvent.ACTION_DOWN == event.getAction()) {
                    searchKeyword(keywordInput.getText().toString());
                }
                return false;
            });

            try {
                searchHistory = JsonUtil.jsonToArrayList(
                        new JSONArray(SharedPreferencesUtil.getString(SharedPreferencesUtil.search_history, "[]")),
                        false);
            } catch (JSONException e) {
                runOnUiThread(() -> MsgUtil.err(e));
                searchHistory = new ArrayList<>();
            }
            searchHistoryAdapter = new SearchHistoryAdapter(this, searchHistory);
            searchHistoryAdapter.setOnClickListener(position -> keywordInput.setText(searchHistory.get(position)));
            searchHistoryAdapter.setOnLongClickListener(position -> {
                MsgUtil.showMsg("删除成功");
                searchHistory.remove(position);
                searchHistoryAdapter.notifyItemRemoved(position);
                searchHistoryAdapter.notifyItemRangeChanged(position, searchHistory.size() - position);
                SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history,
                        new JSONArray(searchHistory).toString());
            });
            historyRecyclerview.setLayoutManager(new CustomLinearManager(this));
            historyRecyclerview.setAdapter(searchHistoryAdapter);
            if (searchHistory.size() > 4) {
                historyRecyclerview.setFocusable(true);
                historyRecyclerview.setFocusableInTouchMode(true);
                historyRecyclerview.requestFocus();
            }

            // 初始化搜索建议
            searchSuggestions = new ArrayList<>();
            searchSuggestionsAdapter = new SearchSuggestionsAdapter(this, searchSuggestions);
            searchSuggestionsAdapter.setOnClickListener(position -> {
                String suggestion = searchSuggestions.get(position);
                keywordInput.setText(suggestion);
                keywordInput.setSelection(suggestion.length());
                searchKeyword(suggestion);
            });
            suggestionsRecyclerview.setLayoutManager(new CustomLinearManager(this));
            suggestionsRecyclerview.setAdapter(searchSuggestionsAdapter);

            // 添加输入监听器获取搜索建议
            if (suggestionsEnabled) {
                keywordInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        String keyword = s.toString();

                        // 移除之前的请求
                        if (suggestionRunnable != null) {
                            handler.removeCallbacks(suggestionRunnable);
                        }

                        if (keyword.isEmpty()) {
                            // 输入为空时显示历史记录
                            runOnUiThread(() -> {
                                if (keywordInput.hasFocus()) {
                                    historyRecyclerview.setVisibility(View.VISIBLE);
                                    suggestionsRecyclerview.setVisibility(View.GONE);
                                }
                            });
                        } else {
                            suggestionRunnable = () -> new Thread(() -> {
                                try {
                                    ArrayList<String> suggestions = SearchApi.getSearchSuggestions(keyword);
                                    runOnUiThread(() -> {
                                        if (keywordInput.hasFocus()) {
                                            searchSuggestions.clear();
                                            searchSuggestions.addAll(suggestions);
                                            searchSuggestionsAdapter.notifyDataSetChanged();

                                            if (!suggestions.isEmpty()) {
                                                historyRecyclerview.setVisibility(View.GONE);
                                                suggestionsRecyclerview.setVisibility(View.VISIBLE);
                                            } else {
                                                historyRecyclerview.setVisibility(View.VISIBLE);
                                                suggestionsRecyclerview.setVisibility(View.GONE);
                                            }
                                        }
                                    });
                                } catch (Exception e) {
                                    Log.e("SearchActivity", "获取搜索建议失败", e);
                                }
                            }).start();
                            handler.postDelayed(suggestionRunnable, 300);
                        }
                    }
                });
            }

            if (getIntent().getStringExtra("keyword") != null) {
                findViewById(R.id.top).setOnClickListener(view1 -> finish());
                keywordInput.setText(getIntent().getStringExtra("keyword"));
                MsgUtil.showMsg("可点击标题栏返回详情页");
            }
            if (savedInstanceState != null) {
                keywordInput.setText(savedInstanceState.getString("search_input", lastKeyword));
                viewPager.setCurrentItem(savedInstanceState.getInt("search_category", 0), false);
                if (hasSearched) {
                    keywordInput.clearFocus();
                    historyRecyclerview.setVisibility(View.GONE);
                    suggestionsRecyclerview.setVisibility(View.GONE);
                }
            }
        });
    }

    public boolean jumpToTargetId(View view) {
        String text = keywordInput.getText().toString();
        LinkUrlUtil.handleId(this, text);
        return true;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void searchKeyword(String str) {
        if (str.contains("Robin") || str.contains("robin")) {
            if (str.contains("撅")) {
                MsgUtil.showText("特殊彩蛋", getString(R.string.egg_special));
                return;
            }
            if (str.contains("纳西妲")) {
                MsgUtil.showText("特殊彩蛋", getString(R.string.egg_robin_nahida));
                return;
            }
        }
        for (String s : specialList) {
            if (str.contains(s)) {
                MsgUtil.showText("特殊彩蛋", getString(R.string.egg_warmwords_warmworld));
                break;
            }
        }

        if (!refreshing) {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View curFocus;
            if ((curFocus = getCurrentFocus()) != null) {
                manager.hideSoftInputFromWindow(curFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }

            if (str.isEmpty()) {
                if (defaultSearchContentEnabled && defaultSearchContent != null && !defaultSearchContent.isEmpty()) {
                    str = defaultSearchContent;
                } else {
                    runOnUiThread(() -> MsgUtil.showMsg("还没输入内容喵~"));
                    return;
                }
            }
            
            setSearchCategoriesVisible(true);
            categoryChangedThisDrag = true;
            categoryScrollDistance = 0;
            if (hasSearched && Objects.equals(lastKeyword, str)) {
                runOnUiThread(() -> {
                    keywordInput.clearFocus();
                    historyRecyclerview.setVisibility(View.GONE);
                });
                requestFragmentFocus();
            } else {
                refreshing = true;
                lastKeyword = str;
                hasSearched = true;

                // 搜索记录
                runOnUiThread(() -> {
                    historyRecyclerview.setVisibility(View.GONE);
                    keywordInput.clearFocus();
                });

                if (!searchHistory.contains(str)) {
                    try {
                        searchHistory.add(0, str);
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history,
                                new JSONArray(searchHistory).toString());
                        runOnUiThread(() -> {
                            searchHistoryAdapter.notifyItemInserted(0);
                            searchHistoryAdapter.notifyItemRangeChanged(0, searchHistory.size());
                            historyRecyclerview.scrollToPosition(0);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> MsgUtil.err(e));
                    }
                } else {
                    try {
                        int pos = searchHistory.indexOf(str);
                        searchHistory.remove(str);
                        searchHistory.add(0, str);
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.search_history,
                                new JSONArray(searchHistory).toString());
                        runOnUiThread(() -> {
                            searchHistoryAdapter.notifyItemMoved(pos, 0);
                            searchHistoryAdapter.notifyItemRangeChanged(0, searchHistory.size());
                            historyRecyclerview.scrollToPosition(0);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> MsgUtil.err(e));
                    }
                }

                try {
                    for (int i = 0; i < searchCategories.length; i++) {
                        Fragment fragmentById = getSupportFragmentManager().findFragmentByTag("f" + i);
                        if (fragmentById != null)
                            ((SearchFragment) fragmentById).update(str);
                    }
                    Fragment fragmentCurr = getSupportFragmentManager()
                            .findFragmentByTag("f" + viewPager.getCurrentItem());
                    if (fragmentCurr != null) {
                        ((SearchFragment) fragmentCurr).refresh();
                        requestFragmentFocus();
                    }
                } catch (Exception e) {
                    report(e);
                }
                refreshing = false;

                if (tutorial_show) {
                    runOnUiThread(() -> {
                        TextView textView = findViewById(R.id.text_tutorial_pager);
                        textView.setVisibility(View.VISIBLE);
                        textView.setText(getString(R.string.tutorial_pager, searchCategories.length));
                    });
                }
            }
        }
    }

    public void onSearchFragmentReady(SearchFragment fragment) {
        if (!hasSearched || viewPager == null || fragment != getSupportFragmentManager()
                .findFragmentByTag("f" + viewPager.getCurrentItem())) return;
        if (!Objects.equals(fragment.keyword, lastKeyword)) fragment.update(lastKeyword);
        fragment.refresh();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("search_keyword", lastKeyword);
        outState.putBoolean("search_submitted", hasSearched);
        if (keywordInput != null) outState.putString("search_input", keywordInput.getText().toString());
        if (viewPager != null) outState.putInt("search_category", viewPager.getCurrentItem());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        if (categoryPicker != null) categoryPicker.dismiss();
        if (categoryMediator != null) categoryMediator.detach();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public void onScrolled(int dy) {
        // 搜索框固定显示；列表滚动不再触发隐藏、上移或恢复动画。
        if (searchBar != null) {
            if (searchBar.getVisibility() != View.VISIBLE) {
                searchBar.setVisibility(View.VISIBLE);
            }
            if (searchBar.getTranslationY() != 0f) {
                searchBar.setTranslationY(0f);
            }
        }
        if (!hasSearched || resultsScrollState == RecyclerView.SCROLL_STATE_IDLE
                || categoryChangedThisDrag || dy == 0 || categoriesBar == null || keywordInput.hasFocus()) return;
        if ((categoryScrollDistance > 0) != (dy > 0)) categoryScrollDistance = 0;
        categoryScrollDistance += dy;
        int threshold = Math.round(24 * getResources().getDisplayMetrics().density);
        if (Math.abs(categoryScrollDistance) >= threshold) {
            boolean visible = categoryScrollDistance < 0;
            if (visible != (categoriesBar.getVisibility() == View.VISIBLE)) {
                setSearchCategoriesVisible(visible);
                // Changing viewport height can itself emit onScrolled. Only one
                // header change per physical drag prevents feedback and flicker.
                categoryChangedThisDrag = true;
            }
        }
    }

    public void onSearchScrollState(SearchFragment source, int state, boolean atTop) {
        if (!isCurrentSearchFragment(source)) return;
        resultsScrollState = state;
        if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
            categoryScrollDistance = 0; categoryChangedThisDrag = false;
        }
        if (atTop && (state == RecyclerView.SCROLL_STATE_IDLE || state == RecyclerView.SCROLL_STATE_DRAGGING)) {
            boolean wasHidden = categoriesBar != null && categoriesBar.getVisibility() != View.VISIBLE;
            setSearchCategoriesVisible(true);
            if (wasHidden) categoryChangedThisDrag = true;
        }
    }

    public void onSearchScrolled(SearchFragment source, int dy) {
        if (isCurrentSearchFragment(source)) onScrolled(dy);
    }

    private boolean isCurrentSearchFragment(SearchFragment source) {
        return viewPager != null && source == getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
    }

    private void setSearchCategoriesVisible(boolean visible) {
        if (categoriesBar == null) return;
        categoriesBar.setVisibility(visible && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS, false)
                ? View.VISIBLE : View.GONE);
    }

    private void updateSearchCategory(int position) {
        if (categorySelector == null || searchCategories == null || position < 0 || position >= searchCategories.length) return;
        categorySelector.setText(searchCategories[position]);
        categorySelector.setContentDescription(getString(R.string.search_category_picker_desc, searchCategories[position]));
    }

    private void showCategoryPicker() {
        if (viewPager == null || categorySelector == null || isFinishing()) return;
        if (categoryPicker != null) categoryPicker.dismiss();
        categoryPicker = new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this, categorySelector);
        for (int i = 0; i < searchCategories.length; i++)
            categoryPicker.getMenu().add(0, 1000 + i, i, searchCategories[i]).setCheckable(true).setChecked(i == viewPager.getCurrentItem());
        categoryPicker.setOnMenuItemClickListener(item -> {
            int position = item.getItemId() - 1000;
            if (position < 0 || position >= searchCategories.length) return false;
            viewPager.setCurrentItem(position, false);
            updateSearchCategory(position);
            setSearchCategoriesVisible(true); categoryChangedThisDrag = true;
            return true;
        });
        categoryPicker.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (categorySelector != null) {
            setSearchCategoriesVisible(true);
            updateSearchCategory(viewPager.getCurrentItem());
            setRound();
        }
    }

    @Override public void setRound() {
        // This page has a compact title + category selector. Keep both usable
        // when the round layout places the clock on its own line.
        View heading = findViewById(R.id.search_heading);
        TextView clock = findViewById(R.id.timeText);
        if (heading == null || clock == null) return;
        if (searchClockTextSize <= 0) searchClockTextSize = clock.getTextSize();
        float density = getResources().getDisplayMetrics().density;
        boolean round = SharedPreferencesUtil.getBoolean("player_ui_round", false);
        View content = findViewById(android.R.id.content);
        int available = content != null && content.getWidth() > 0 ? content.getWidth() : getResources().getDisplayMetrics().widthPixels;
        if (window_width > 0) available = Math.min(available, window_width);
        boolean stacked = round || available / density < 128 * Math.max(1f, getResources().getConfiguration().fontScale);
        android.widget.RelativeLayout.LayoutParams time = new android.widget.RelativeLayout.LayoutParams(-2, -2);
        android.widget.RelativeLayout.LayoutParams row = new android.widget.RelativeLayout.LayoutParams(-1, -2);
        if (stacked) {
            time.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
            row.addRule(android.widget.RelativeLayout.BELOW, R.id.timeText);
            int inset = round ? Math.max(Math.round(8 * density), Math.round(available * .12f))
                    : Math.min(Math.round(6 * density), Math.round(available * .04f));
            heading.setPadding(inset, 0, inset, 0);
            clock.setTextSize(12); clock.setAlpha(round ? .85f : 1f);
        } else {
            time.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
            time.rightMargin = Math.round(6 * density);
            time.topMargin = time.bottomMargin = Math.round(3 * density);
            row.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
            row.addRule(android.widget.RelativeLayout.LEFT_OF, R.id.timeText);
            row.leftMargin = Math.round(6 * density); row.rightMargin = Math.round(2 * density);
            heading.setPadding(0, 0, 0, 0);
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, searchClockTextSize); clock.setAlpha(1f);
        }
        clock.setLayoutParams(time); heading.setLayoutParams(row);
    }

    private void requestFragmentFocus(){
        Fragment fragmentCurr = getSupportFragmentManager()
                .findFragmentByTag("f" + viewPager.getCurrentItem());
        if (fragmentCurr != null) {
            ((SearchFragment) fragmentCurr).refresh();
            if (fragmentCurr.getView() != null) {
                View recyclerView = fragmentCurr.getView().findViewById(R.id.recyclerView);
                recyclerView.setFocusable(true);
                recyclerView.setFocusableInTouchMode(true);
                recyclerView.requestFocus();
            }
        }
    }
}
