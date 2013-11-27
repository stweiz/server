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

package org.osiam.resources.provisioning

import org.osiam.resources.converter.GroupConverter
import org.osiam.resources.exceptions.ResourceExistsException
import org.osiam.resources.exceptions.ResourceNotFoundException
import org.osiam.resources.scim.Group
import org.osiam.storage.dao.GroupDao
import org.osiam.storage.dao.SearchResult
import org.osiam.storage.entities.GroupEntity
import org.osiam.storage.entities.MetaEntity
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

class SCIMGroupProvisioningBeanSpec extends Specification {

    def groupDao = Mock(GroupDao)
    private GroupConverter groupConverter = new GroupConverter(groupDao: groupDao)
    SCIMGroupProvisioningBean underTest = new SCIMGroupProvisioningBean(groupDao: groupDao, groupConverter: groupConverter)
    def group = Mock(Group)
    def entity = Mock(GroupEntity)

    def "should return with id enriched group on create"() {
        given:
        groupDao.getById(_) >> { throw new ResourceNotFoundException('') }
        group.getId() >> UUID.randomUUID().toString()
        group.getMembers() >> Collections.emptySet()

        when:
        def result = underTest.create(group)

        then:
        result != group
        UUID.fromString(result.id)

    }

    def "should call dao create on create"() {
        given:
        groupDao.getById(_) >> { throw new ResourceNotFoundException('') }
        group.getId() >> UUID.randomUUID().toString()
        group.getMembers() >> Collections.emptySet()

        when:
        underTest.create(group)

        then:
        1 * groupDao.create(_)
    }

    def "should wrap exceptions to org.osiam.resources.exceptions.ResourceExistsException on create"() {
        given:
        groupDao.getById(_) >> { throw new ResourceNotFoundException('') }
        group.getId() >> UUID.randomUUID().toString()
        group.getMembers() >> Collections.emptySet()
        groupDao.create(_) >> {
            throw new DataIntegrityViolationException("moep")
        }
        group.getDisplayName() >> "displayName"
        when:
        underTest.create(group)
        then:
        def e = thrown(ResourceExistsException)
        e.message == "displayName already exists."
    }

    def "should call dao delete on delete"() {
        when:
        underTest.delete("id")

        then:
        1 * groupDao.delete("id")

    }

    def "should call dao search on search"() {
        given:

        GroupEntity groupEntityMock = Mock()
        groupEntityMock.getId() >> UUID.randomUUID()
        groupEntityMock.getMeta() >> new MetaEntity()
        groupEntityMock.getMembers() >> ([] as Set)
        def groupList = [groupEntityMock] as List
        def searchResult = new SearchResult(groupList, 1000)
        groupDao.search("anyFilter", "userName", "ascending", 100, 0) >> searchResult

        when:
        def result = underTest.search("anyFilter", "userName", "ascending", 100, 1)

        then:
        result.getTotalResults() == 1000.toLong()
    }
}