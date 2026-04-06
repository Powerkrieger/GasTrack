package com.example.gastrack.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GasTrackDatabase(context: Context) : SQLiteOpenHelper(context, "gastrack.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE stations (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                city TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE fuel_entries (
                id TEXT PRIMARY KEY,
                station_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                liters REAL NOT NULL,
                euros REAL NOT NULL,
                price_per_liter REAL NOT NULL,
                kilometers REAL NOT NULL DEFAULT 0,
                receipt_path TEXT,
                synced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(station_id) REFERENCES stations(id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN kilometers REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        }
    }
}
