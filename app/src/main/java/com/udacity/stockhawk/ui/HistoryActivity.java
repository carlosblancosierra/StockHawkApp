package com.udacity.stockhawk.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.udacity.stockhawk.R;
import com.udacity.stockhawk.data.Contract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;

public class HistoryActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks {

    @BindView(R.id.graph_stock_name)
    TextView mGraphStockSymbol;

    @BindView(R.id.graph)
    GraphView mGraph;

    private String symbol = "";
    private int STOCK_HISTORY_LOADER = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ButterKnife.bind(this);

        symbol = getIntent().getStringExtra("symbol");
        mGraphStockSymbol.setText(symbol);

        getSupportLoaderManager().initLoader(STOCK_HISTORY_LOADER, null, this);

    }

    @Override
    public Loader onCreateLoader(int id, final Bundle args) {

        if (id == STOCK_HISTORY_LOADER) {
            return new CursorLoader(this,
                    Contract.Quote.makeUriForStock(symbol),
                    Contract.Quote.QUOTE_COLUMNS.toArray(new String[]{}),
                    null, null, Contract.Quote.COLUMN_SYMBOL);
        } else {
            return null;
        }
    }


    @Override
    public void onLoadFinished(Loader loader, Object data) {

        if (loader.getId() == STOCK_HISTORY_LOADER) {

            Cursor cursor = (Cursor) data;

//            if (cursor.getCount() != 0) {
//                error.setVisibility(View.GONE);
//            }

            LineGraphSeries series = graphStock(cursor);

            if (series != null) {
                mGraph.addSeries(series);

                mGraph.getViewport().setMinX(series.getLowestValueX());
                mGraph.getViewport().setMaxX(series.getHighestValueX());
                mGraph.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(this, new SimpleDateFormat("MMMyy")));


                mGraph.getViewport().setMinY(series.getLowestValueY());
                mGraph.getViewport().setMaxY(series.getHighestValueY());

                mGraph.getGridLabelRenderer().setNumVerticalLabels(20);

            }
        }
    }

    private LineGraphSeries<DataPoint> graphStock(Cursor cursor) {

        if (cursor.moveToFirst()) {

            int historyIndex = cursor.getColumnIndex(Contract.Quote.COLUMN_HISTORY);

            String history = cursor.getString(historyIndex);
            mGraphStockSymbol.setText(symbol);

            try {
                JSONArray root = new JSONArray(history);

                LineGraphSeries<DataPoint> lineGraphSeries = new LineGraphSeries<>();
                ArrayList<DataPoint> dpArrayList = new ArrayList<>();

                for (int i = 0; i < root.length(); i++) {

                    JSONObject currentObject = root.getJSONObject(i);
                    long dateLong = currentObject.getLong("date");
                    double price = currentObject.getDouble("price");

                    Date date = new Date(dateLong);

                    dpArrayList.add(0, new DataPoint(date, price));
                }

                for (DataPoint dp : dpArrayList){
                    lineGraphSeries.appendData(dp, true, 200);
                }

                return lineGraphSeries;


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    @Override
    public void onLoaderReset(Loader loader) {

        if (loader.getId() == STOCK_HISTORY_LOADER) {
        }
    }
}
