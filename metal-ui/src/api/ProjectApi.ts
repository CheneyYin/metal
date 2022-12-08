import axios from "axios";
import {ApiResponse, ApiResponseEntity, timeout} from "./APIs";
import {Deploy, Project} from "../model/Project";
import _ from "lodash"

const instance = axios.create({
    headers: {
        'Content-Type': 'application/json'
    },
    timeout: timeout()
})

function idMap <T> (obj: any): T {
    obj = _.mapKeys(obj, (val, key) => {
        return key === '_id'? 'id': key
    })
    const target: T = obj
    return target;
}

function projectMap (obj: any): Project {
    obj.user = _.mapKeys(obj.user, (val, key) => {
        return key === '_id'? 'id': key
    })

    obj = _.mapKeys(obj, (val, key) => {
        return key === '_id'? 'id': key
    })
    const proj: Project = obj
    return proj
}

export async function getAllProjectOfUser(token: string): Promise<Project[]> {
    const url = '/api/v1/projects'
    return instance.get(url, {
        headers: {
            "Authorization": `Bearer ${token}`
        }
    }).then(response => {
        try {
            const resp: ApiResponseEntity = response.data
            if (!ApiResponse.isSuccess(resp)) {
                if (resp.msg === undefined) {
                    throw new Error('Response is failure, and no msg found in response.')
                }
                throw new Error(resp.msg)
            }
            if (resp.data === undefined) {
                throw new Error('Response is successful, but no data found in response.')
            }
            const result: Project[] = resp.data.map(projectMap)
            const projects: Project[] = result
            return projects
        } catch (err) {
            return Promise.reject(err)
        }
    })
}

export async function getProjectById(token: string, id:string) {
    const url = `/api/v1/projects/id/${id}`;
    return instance.get(url, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    }).then(response => {
        try {
            const resp: ApiResponseEntity = response.data
            if (!ApiResponse.isSuccess(resp)) {
                if (resp.msg === undefined) {
                    throw new Error('Response is failure, and no msg found in response.')
                }
                throw new Error(resp.msg)
            }
            if (resp.data === undefined) {
                throw new Error('Response is successful, but no data found in response.')
            }
            const project: Project = projectMap(resp.data);
            return project;
        } catch (err) {
            return Promise.reject(err)
        }
    })
}



export interface ProjectParams {
    name?: string,
    pkgs?: string[],
    platform?: any;
    backendArgs?: string[],
}

export async function updateProjectDetail(token:string, id: string, params: ProjectParams) {
    const url = `/api/v1/projects/id/${id}`;
    return instance.put(url, params, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    }).then(response => {
        try {
            const resp: ApiResponseEntity = response.data
            if (!ApiResponse.isSuccess(resp)) {
                if (resp.msg === undefined) {
                    throw new Error('Response is failure, and no msg found in response.')
                }
                throw new Error(resp.msg)
            }
            if (resp.data === undefined) {
                throw new Error('Response is successful, but no data found in response.')
            }
            return id;
        } catch (err) {
            return Promise.reject(err);
        }
    })
}

export async function createProject(token:string, params: ProjectParams) {
    const url = "/api/v1/projects";
    return instance.post(url, params, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    }).then(response => {
        try {
            const resp: ApiResponseEntity = response.data
            if (!ApiResponse.isSuccess(resp)) {
                if (resp.msg === undefined) {
                    throw new Error('Response is failure, and no msg found in response.')
                }
                throw new Error(resp.msg)
            }
            if (resp.data === undefined) {
                throw new Error('Response is successful, but no data found in response.')
            }
            const projectId: string = resp.data;
            return projectId;
        } catch (err) {
            return Promise.reject(err)
        }
    });
}

export async function updateProject(token:string, id: string, params: ProjectParams) {
    const url = `/api/v1/projects/id/${id}`;
    return instance.put(url, params, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    }).then(response => {
        try {
            const resp: ApiResponseEntity = response.data
            if (!ApiResponse.isSuccess(resp)) {
                if (resp.msg === undefined) {
                    throw new Error('Response is failure, and no msg found in response.')
                }
                throw new Error(resp.msg)
            }
            if (resp.data === undefined) {
                throw new Error('Response is successful, but no data found in response.')
            }
            const projectId: string = resp.data;
            return projectId;
        } catch (err) {
            return Promise.reject(err)
        }
    });
}

export async function getDeploy(token:string, deployId: string) {
    const url = `/api/v1/projects/deploy/${deployId}`;
    return instance.get(url, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    }).then(response => {
        try {
            const resp: ApiResponseEntity = response.data
            ApiResponse.mayBeFailure(resp);
            const deploy: Deploy = idMap<Deploy>(resp.data);
            return deploy;
        } catch (err) {
            return Promise.reject(err)
        }
    });
}