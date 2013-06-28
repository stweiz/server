/*
 * Copyright (C) 2013 tarent AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.osiam.storage.entities;

import org.osiam.resources.scim.MultiValuedAttribute;

import javax.persistence.*;

/**
 * Instant messaging Entity
 */
@Entity(name = "scim_im")
public class ImEntity extends MultiValueAttributeEntitySkeleton implements ChildOfMultiValueAttributeWithType, HasUser {

    @Column
    @Enumerated(EnumType.STRING)
    private CanonicalImTypes type;

    @ManyToOne
    private UserEntity user;


    public String getType() {
        if(type != null) {
            return type.toString();
        }
        return null;
    }

    public void setType(String type) {
        if(type != null) {
            this.type = CanonicalImTypes.valueOf(type);
        }
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public MultiValuedAttribute toScim() {
        return new MultiValuedAttribute.Builder().
                setType(getType()).
                setValue(getValue()).
                build();
    }

    public static ImEntity fromScim(MultiValuedAttribute multiValuedAttribute) {
        ImEntity imEntity = new ImEntity();
        imEntity.setType(multiValuedAttribute.getType());
        imEntity.setValue(String.valueOf(multiValuedAttribute.getValue()));
        return imEntity;
    }

    private enum CanonicalImTypes {
        aim, gtalk, icq, xmpp, msn, skype, qq, yahoo
    }
}