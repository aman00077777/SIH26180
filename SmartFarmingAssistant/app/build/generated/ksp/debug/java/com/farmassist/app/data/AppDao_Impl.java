package com.farmassist.app.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDao_Impl implements AppDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PredictionRecord> __insertionAdapterOfPredictionRecord;

  private final EntityInsertionAdapter<SensorReadingRecord> __insertionAdapterOfSensorReadingRecord;

  private final SharedSQLiteStatement __preparedStmtOfClearAllPredictions;

  private final SharedSQLiteStatement __preparedStmtOfClearAllSensorReadings;

  public AppDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPredictionRecord = new EntityInsertionAdapter<PredictionRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `predictions` (`id`,`timestamp`,`type`,`className`,`confidence`,`recommendation`,`cropSelected`,`acres`,`doseText`,`imagePath`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PredictionRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getClassName());
        statement.bindDouble(5, entity.getConfidence());
        statement.bindString(6, entity.getRecommendation());
        if (entity.getCropSelected() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCropSelected());
        }
        if (entity.getAcres() == null) {
          statement.bindNull(8);
        } else {
          statement.bindDouble(8, entity.getAcres());
        }
        if (entity.getDoseText() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getDoseText());
        }
        if (entity.getImagePath() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getImagePath());
        }
      }
    };
    this.__insertionAdapterOfSensorReadingRecord = new EntityInsertionAdapter<SensorReadingRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `sensor_readings` (`id`,`timestamp`,`soilMoisturePercent`,`temperatureCelsius`,`humidityPercent`,`irrigationAdvice`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SensorReadingRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindDouble(3, entity.getSoilMoisturePercent());
        statement.bindDouble(4, entity.getTemperatureCelsius());
        statement.bindDouble(5, entity.getHumidityPercent());
        statement.bindString(6, entity.getIrrigationAdvice());
      }
    };
    this.__preparedStmtOfClearAllPredictions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM predictions";
        return _query;
      }
    };
    this.__preparedStmtOfClearAllSensorReadings = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM sensor_readings";
        return _query;
      }
    };
  }

  @Override
  public Object insertPrediction(final PredictionRecord record,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfPredictionRecord.insertAndReturnId(record);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSensorReading(final SensorReadingRecord record,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSensorReadingRecord.insertAndReturnId(record);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAllPredictions(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAllPredictions.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAllPredictions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAllSensorReadings(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAllSensorReadings.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAllSensorReadings.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PredictionRecord>> getAllPredictions() {
    final String _sql = "SELECT * FROM predictions ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"predictions"}, new Callable<List<PredictionRecord>>() {
      @Override
      @NonNull
      public List<PredictionRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfClassName = CursorUtil.getColumnIndexOrThrow(_cursor, "className");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfRecommendation = CursorUtil.getColumnIndexOrThrow(_cursor, "recommendation");
          final int _cursorIndexOfCropSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "cropSelected");
          final int _cursorIndexOfAcres = CursorUtil.getColumnIndexOrThrow(_cursor, "acres");
          final int _cursorIndexOfDoseText = CursorUtil.getColumnIndexOrThrow(_cursor, "doseText");
          final int _cursorIndexOfImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "imagePath");
          final List<PredictionRecord> _result = new ArrayList<PredictionRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PredictionRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpClassName;
            _tmpClassName = _cursor.getString(_cursorIndexOfClassName);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            final String _tmpRecommendation;
            _tmpRecommendation = _cursor.getString(_cursorIndexOfRecommendation);
            final String _tmpCropSelected;
            if (_cursor.isNull(_cursorIndexOfCropSelected)) {
              _tmpCropSelected = null;
            } else {
              _tmpCropSelected = _cursor.getString(_cursorIndexOfCropSelected);
            }
            final Double _tmpAcres;
            if (_cursor.isNull(_cursorIndexOfAcres)) {
              _tmpAcres = null;
            } else {
              _tmpAcres = _cursor.getDouble(_cursorIndexOfAcres);
            }
            final String _tmpDoseText;
            if (_cursor.isNull(_cursorIndexOfDoseText)) {
              _tmpDoseText = null;
            } else {
              _tmpDoseText = _cursor.getString(_cursorIndexOfDoseText);
            }
            final String _tmpImagePath;
            if (_cursor.isNull(_cursorIndexOfImagePath)) {
              _tmpImagePath = null;
            } else {
              _tmpImagePath = _cursor.getString(_cursorIndexOfImagePath);
            }
            _item = new PredictionRecord(_tmpId,_tmpTimestamp,_tmpType,_tmpClassName,_tmpConfidence,_tmpRecommendation,_tmpCropSelected,_tmpAcres,_tmpDoseText,_tmpImagePath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<SensorReadingRecord>> getAllSensorReadings() {
    final String _sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sensor_readings"}, new Callable<List<SensorReadingRecord>>() {
      @Override
      @NonNull
      public List<SensorReadingRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSoilMoisturePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "soilMoisturePercent");
          final int _cursorIndexOfTemperatureCelsius = CursorUtil.getColumnIndexOrThrow(_cursor, "temperatureCelsius");
          final int _cursorIndexOfHumidityPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "humidityPercent");
          final int _cursorIndexOfIrrigationAdvice = CursorUtil.getColumnIndexOrThrow(_cursor, "irrigationAdvice");
          final List<SensorReadingRecord> _result = new ArrayList<SensorReadingRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SensorReadingRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpSoilMoisturePercent;
            _tmpSoilMoisturePercent = _cursor.getFloat(_cursorIndexOfSoilMoisturePercent);
            final float _tmpTemperatureCelsius;
            _tmpTemperatureCelsius = _cursor.getFloat(_cursorIndexOfTemperatureCelsius);
            final float _tmpHumidityPercent;
            _tmpHumidityPercent = _cursor.getFloat(_cursorIndexOfHumidityPercent);
            final String _tmpIrrigationAdvice;
            _tmpIrrigationAdvice = _cursor.getString(_cursorIndexOfIrrigationAdvice);
            _item = new SensorReadingRecord(_tmpId,_tmpTimestamp,_tmpSoilMoisturePercent,_tmpTemperatureCelsius,_tmpHumidityPercent,_tmpIrrigationAdvice);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<SensorReadingRecord>> getRecentSensorReadings() {
    final String _sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT 50";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sensor_readings"}, new Callable<List<SensorReadingRecord>>() {
      @Override
      @NonNull
      public List<SensorReadingRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSoilMoisturePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "soilMoisturePercent");
          final int _cursorIndexOfTemperatureCelsius = CursorUtil.getColumnIndexOrThrow(_cursor, "temperatureCelsius");
          final int _cursorIndexOfHumidityPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "humidityPercent");
          final int _cursorIndexOfIrrigationAdvice = CursorUtil.getColumnIndexOrThrow(_cursor, "irrigationAdvice");
          final List<SensorReadingRecord> _result = new ArrayList<SensorReadingRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SensorReadingRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final float _tmpSoilMoisturePercent;
            _tmpSoilMoisturePercent = _cursor.getFloat(_cursorIndexOfSoilMoisturePercent);
            final float _tmpTemperatureCelsius;
            _tmpTemperatureCelsius = _cursor.getFloat(_cursorIndexOfTemperatureCelsius);
            final float _tmpHumidityPercent;
            _tmpHumidityPercent = _cursor.getFloat(_cursorIndexOfHumidityPercent);
            final String _tmpIrrigationAdvice;
            _tmpIrrigationAdvice = _cursor.getString(_cursorIndexOfIrrigationAdvice);
            _item = new SensorReadingRecord(_tmpId,_tmpTimestamp,_tmpSoilMoisturePercent,_tmpTemperatureCelsius,_tmpHumidityPercent,_tmpIrrigationAdvice);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
