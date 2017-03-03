package com.udacity.stockhawk.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.udacity.stockhawk.R;
import com.udacity.stockhawk.data.Contract;
import com.udacity.stockhawk.data.PrefUtils;
import com.udacity.stockhawk.sync.QuoteSyncJob;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.quotes.stock.StockQuote;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks,
        SwipeRefreshLayout.OnRefreshListener,
        StockAdapter.StockAdapterOnClickHandler {

    private static final int STOCK_LOADER = 0;
    private static final int CHECK_SYMBOL_LOADER_ID = 444;
    private static final String SYMBOL_LOADER_KEY = "new_symbol";

    private static Integer SYMBOL_NOT_CHECKED = 0;
    private static Integer SYMBOL_VALID = 1;
    private static Integer SYMBOL_INVALID = 2;


    private boolean mSymbolExists;

    @SuppressWarnings("WeakerAccess")
    @BindView(R.id.recycler_view)
    RecyclerView stockRecyclerView;
    @SuppressWarnings("WeakerAccess")
    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @SuppressWarnings("WeakerAccess")
    @BindView(R.id.error)
    TextView error;
    private StockAdapter adapter;

    @Override
    public void onClick(String symbol) {
        Timber.d("Symbol clicked: %s", symbol);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        adapter = new StockAdapter(this, this);
        stockRecyclerView.setAdapter(adapter);
        stockRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setRefreshing(true);
        onRefresh();

        QuoteSyncJob.initialize(this);
        getSupportLoaderManager().initLoader(STOCK_LOADER, null, this);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                String symbol = adapter.getSymbolAtPosition(viewHolder.getAdapterPosition());
                PrefUtils.removeStock(MainActivity.this, symbol);
                getContentResolver().delete(Contract.Quote.makeUriForStock(symbol), null, null);
            }
        }).attachToRecyclerView(stockRecyclerView);


    }

    private boolean networkUp() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    @Override
    public void onRefresh() {

        QuoteSyncJob.syncImmediately(this);

        if (!networkUp() && adapter.getItemCount() == 0) {
            swipeRefreshLayout.setRefreshing(false);
            error.setText(getString(R.string.error_no_network));
            error.setVisibility(View.VISIBLE);
        } else if (!networkUp()) {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, R.string.toast_no_connectivity, Toast.LENGTH_LONG).show();
        } else if (PrefUtils.getStocks(this).size() == 0) {
            swipeRefreshLayout.setRefreshing(false);
            error.setText(getString(R.string.error_no_stocks));
            error.setVisibility(View.VISIBLE);
        } else {
            error.setVisibility(View.GONE);
        }
    }

    public void button(@SuppressWarnings("UnusedParameters") View view) {
        new AddStockDialog().show(getFragmentManager(), "StockDialogFragment");
    }

    void addStock(String symbol) {
        if (symbol != null && !symbol.isEmpty()) {

            if (networkUp()) {
                swipeRefreshLayout.setRefreshing(true);
            } else {
                String message = getString(R.string.toast_stock_added_no_connectivity, symbol);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }

            //TODO: check if symbol exists

            Bundle newSymbolBundle = new Bundle();
            newSymbolBundle.putString(SYMBOL_LOADER_KEY, symbol);
            getSupportLoaderManager().restartLoader(CHECK_SYMBOL_LOADER_ID, newSymbolBundle, this);
            getSupportLoaderManager().initLoader(CHECK_SYMBOL_LOADER_ID, newSymbolBundle, this);


        }
    }

    @Override
    public Loader onCreateLoader(int id, final Bundle args) {

        if (id == STOCK_LOADER) {
            return new CursorLoader(this,
                    Contract.Quote.URI,
                    Contract.Quote.QUOTE_COLUMNS.toArray(new String[]{}),
                    null, null, Contract.Quote.COLUMN_SYMBOL);
        }

        if (id == CHECK_SYMBOL_LOADER_ID) {

            return new AsyncTaskLoader(this) {

                Integer result = SYMBOL_NOT_CHECKED;

                @Override
                protected void onStartLoading() {
                    forceLoad();
                }

                @Override
                public Object loadInBackground() {

                    String stockString = args.getString(SYMBOL_LOADER_KEY);

                    try {
                        Stock stockObject = YahooFinance.get(stockString);

                        StockQuote quote = stockObject.getQuote();

                        if (quote.getPrice() == null) {
                            result = SYMBOL_INVALID;
                            return result;
                        } else {
                            PrefUtils.addStock(getContext(), stockString);
                            result = SYMBOL_VALID;
                            return result;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Timber.d("Failed to add stock: " + stockString);
                    }
                    return result;
                }

            };
        }

        return null;
    }


    @Override
    public void onLoadFinished(Loader loader, Object data) {

        if (loader.getId() == STOCK_LOADER) {

            Cursor cursor = (Cursor) data;

            swipeRefreshLayout.setRefreshing(false);

            if (cursor.getCount() != 0) {
                error.setVisibility(View.GONE);
            }
            adapter.setCursor(cursor);
        } else if (loader.getId() == CHECK_SYMBOL_LOADER_ID) {

            Integer result = (Integer) data;
            if (result.equals(SYMBOL_VALID)) {
                Timber.d("Finished Loader, new Symbol Added");
                Toast.makeText(this, "Added Stock", Toast.LENGTH_SHORT).show();
            } else if (result.equals(SYMBOL_INVALID)){
                Timber.d("Finished Loader, Failed to Add Symbol case Invalid");
                Toast.makeText(this, "Invalid Symbol", Toast.LENGTH_SHORT).show();
            } else {
                Timber.d("Finished Loader, Failed to Add Symbol case Not Checked");
            }

            QuoteSyncJob.syncImmediately(this);

        }
    }


    @Override
    public void onLoaderReset(Loader loader) {

        if (loader.getId() == STOCK_LOADER) {
            swipeRefreshLayout.setRefreshing(false);
            adapter.setCursor(null);
        }
    }


    private void setDisplayModeMenuItemIcon(MenuItem item) {
        if (PrefUtils.getDisplayMode(this)
                .equals(getString(R.string.pref_display_mode_absolute_key))) {
            item.setIcon(R.drawable.ic_percentage);
        } else {
            item.setIcon(R.drawable.ic_dollar);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_settings, menu);
        MenuItem item = menu.findItem(R.id.action_change_units);
        setDisplayModeMenuItemIcon(item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_change_units) {
            PrefUtils.toggleDisplayMode(this);
            setDisplayModeMenuItemIcon(item);
            adapter.notifyDataSetChanged();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
