# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Adds a new `useStaticKey` param to `updateSessionInfo_Transaction`
  - This enables smooth switching between `useDynamicAccessTokenSigningKey` settings by allowing refresh calls to
    change the signing key type of a session
- Fixes issue where error logs were printed to StdOut instead of StdErr.

## [1.25.0] - 2023-09-19

- Compatibility with plugin interface 4.0.0

## [1.24.0] - 2023-06-02

- Compatibility with plugin interface 3.0.0

## [1.23.0] - 2023-04-05

- Adds support for plugin inteface version 2.23
- Adds `use_static_key` into `session_info`

### Migration


- If using `access_token_signing_key_dynamic` false:
  - ```
    db.session_info.update({},
      {
        "$set": {
          "use_static_key": true
        }
      });
    ```
  - ```
    db.key_value.aggregate([
      {
        "$match": {
          _id: "access_token_signing_key_list"
        }
      },
      {
        $unwind: "$keys"
      },
      {
        $addFields: {
          _id: {
            "$concat": [
              "s-",
              {
                $convert: {
                  input: "$keys.created_at_time",
                  to: "string"
                }
              }
            ]
          },
          "key_string": "$keys.value",
          "algorithm": "RS256",
          "created_at": "$keys.created_at_time",
          
        }
      },
      {
        "$project": {
          "keys": 0,
          
        }
      },
      {
        "$merge": {
          "into": "jwt_signing_keys",
          
        }
      }
    ]);
    ```

- If using `access_token_signing_key_dynamic` true or not set:
  - ```
    db.session_info.update({},
      {
        "$set": {
          "use_static_key": false
        }
      });
    ```
- Fixed an issue when adding new access token signing key to an empty list

## [1.22.0] - 2023-03-30

- New plugin version (v2.22)

## [1.21.0] - 2022-08-10

- New plugin version (v2.21)

## [1.20.0] - 2022-08-10

- New plugin version (v2.20)

## [1.19.0] - 2022-11-07

- Updates dependencies as per: https://github.com/supertokens/supertokens-core/issues/525

## [1.18.0] - 2022-08-18

- Adds log level feature and compatibility with plugin interface 2.18

## [1.17.0] - 2022-08-10

- New plugin version (v2.17)

## [1.16.0] - 2022-07-25

- New plugin version (v2.16)

## [1.15.0] - 2022-06-07

- Compatibility with plugin interface 2.15 - returns only non expired session handles for a user

## [1.14.0] - 2022-05-05

- New plugin version (v2.14)

## [1.13.0] - 2022-03-17

- Fixes issue with memory leak during tests
- New plugin version (v2.13)

## [1.12.0] - 2022-02-24

### Changed

- Uses new plugin interface (v2.12)
- Add workflow to verify if pr title follows conventional commits

## [1.11.0] - 2022-01-14

### Changed

- Uses new plugin interface (v2.11)

## [1.10.0] - 2021-12-20

### Changed

- Uses new plugin interface for delete sessions based on user_id

## [1.9.0] - 2021-09-20

### Added

- Updated to match 2.9 plugin interface to support multiple access token signing
  keys: https://github.com/supertokens/supertokens-core/issues/305
- Added new type of key into KeyValue collection and the handler methods
- Added functions and other changes for the JWT recipe

## [1.8.0] - 2021-06-23

### Changed

- Uses new plugin interface

## [1.7.1] - 2021-04-23

### Added

- Adds support for multiple hosts in connection URI

## [1.7.0] - 2021-04-20

### Added

- Added ability to set table name prefix (https://github.com/supertokens/supertokens-core/issues/220)

## [1.6.0] - 2021-02-16

### Changed

- Uses new plugin interface

## [1.5.0] - 2021-0114

### Changed

- Used rowmapper interface
- Uses new plugin interface

## [1.4.0] - 2020-11-06

### Added

- Support for plugin interface 2.4

## [1.2.0] - 2020-05-21

### Added

- Adds check to know if in memory db should be used.
