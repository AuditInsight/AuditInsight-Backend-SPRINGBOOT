package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;


@AllArgsConstructor
@NoArgsConstructor
@Data
public class ResponseMessage {
    private HttpStatus Status;
    private String Message;
}
