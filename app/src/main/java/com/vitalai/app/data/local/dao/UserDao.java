package com.vitalai.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vitalai.app.data.local.entity.UserEntity;

import io.reactivex.rxjava3.core.Single;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    Single<Long> insert(UserEntity user);
}