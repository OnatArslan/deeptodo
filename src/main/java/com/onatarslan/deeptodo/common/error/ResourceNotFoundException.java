package com.onatarslan.deeptodo.common.error;

// "Böyle bir kaynak yok ya da sen göremezsin." İkisi bilerek ayırt edilmez:
// üyesi olmadığın bir workspace için 403 yerine 404 dönmek, onun var olduğunu da saklar.
// HTTP tipi taşımaz (spec §13); status eşlemesi GlobalExceptionHandler'da.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
